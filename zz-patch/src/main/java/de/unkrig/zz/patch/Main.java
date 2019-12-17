
/*
 * de.unkrig.patch - An enhanced version of the UNIX PATCH utility
 *
 * Copyright (c) 2011, Arno Unkrig
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 *    1. Redistributions of source code must retain the above copyright notice, this list of conditions and the
 *       following disclaimer.
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 *       following disclaimer in the documentation and/or other materials provided with the distribution.
 *    3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote
 *       products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package de.unkrig.zz.patch;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.regex.Pattern;

import de.unkrig.commons.file.ExceptionHandler;
import de.unkrig.commons.file.contentstransformation.ContentsTransformer;
import de.unkrig.commons.file.filetransformation.FileTransformations;
import de.unkrig.commons.file.filetransformation.FileTransformer;
import de.unkrig.commons.file.org.apache.commons.compress.archivers.ArchiveFormatFactory;
import de.unkrig.commons.file.org.apache.commons.compress.compressors.CompressionFormatFactory;
import de.unkrig.commons.lang.protocol.Mapping;
import de.unkrig.commons.lang.protocol.Mappings;
import de.unkrig.commons.lang.protocol.PredicateUtil;
import de.unkrig.commons.nullanalysis.Nullable;
import de.unkrig.commons.text.LevelFilteredPrinter;
import de.unkrig.commons.text.Printers;
import de.unkrig.commons.text.StringStream.UnexpectedElementException;
import de.unkrig.commons.text.expression.AbstractExpression;
import de.unkrig.commons.text.expression.EvaluationException;
import de.unkrig.commons.text.expression.Expression;
import de.unkrig.commons.text.expression.ExpressionEvaluator;
import de.unkrig.commons.text.expression.ExpressionUtil;
import de.unkrig.commons.text.parser.ParseException;
import de.unkrig.commons.text.pattern.Glob;
import de.unkrig.commons.text.pattern.Pattern2;
import de.unkrig.commons.util.CommandLineOptionException;
import de.unkrig.commons.util.CommandLineOptions;
import de.unkrig.commons.util.annotation.CommandLineOption;
import de.unkrig.commons.util.annotation.CommandLineOptionGroup;
import de.unkrig.commons.util.annotation.RegexFlags;
import de.unkrig.commons.util.logging.SimpleLogging;
import de.unkrig.zz.patch.SubstitutionContentsTransformer.Mode;
import de.unkrig.zz.patch.diff.DiffParser.Hunk;

/**
 * Implementation of a PATCH command line utility with the following features:
 * <ul>
 *   <li>
 *     Transforms regular files, directory trees, and optionally compressed files and entries in archive files (also in
 *     nested ones)
 *   </li>
 *   <li>Reads patch files in NORMAL, CONTEXT and UNIFIED diff format
 *   <li>Can replace the contents of files from "update files"
 *   <li>Can do search-and-replace within files (SED like)
 *   <li>Can transform out-of-place or in-place
 *   <li>Optionally keeps copies of the original files
 *   <li>Can remove files
 *   <li>Can rename files
 * </ul>
 */
public final
class Main {

    private Main() {}

    private static final ExceptionHandler<IOException> PRINT_AND_CONTINUE = new ExceptionHandler<IOException>() {

        @Override public void
        handle(String path, IOException ioe) { Printers.error(path, ioe); }

        @Override public void
        handle(String path, RuntimeException rte) {
            if (rte == FileTransformer.NOT_IDENTICAL) {
                Printers.verbose(path);
            } else {
                Printers.error(path, rte);
            }
        }
    };

    private static final ExceptionHandler<IOException> RETHROW = new ExceptionHandler<IOException>() {

        @Override public void
        handle(String path, IOException ioe) throws IOException { throw ioe; }

        @Override public void
        handle(String path, RuntimeException rte) {
            if (rte == FileTransformer.NOT_IDENTICAL) {
                Printers.verbose(path);
            }
            throw rte;
        }
    };

    /**
     * <h2>Usage</h2>
     *
     * <dl>
     *   <dt>{@code zzpatch} [ <var>option</var> ... ]</dt>
     *   <dd>
     *     Transforms STDIN to STDOUT.
     *   </dd>
     *   <dt>{@code zzpatch} [ <var>option</var> ... ] <var>file-or-dir</var></dt>
     *   <dd>
     *     Transforms <var>file-or-dir</var> in-place.
     *   </dd>
     *   <dt>{@code zzpatch} [ <var>option</var> ... ] <var>file-or-dir</var> <var>new-file-or-dir</var></dt>
     *   <dd>
     *     Transforms <var>file-or-dir</var> into <var>new-file-or-dir</var>.
     *   </dd>
     *   <dt>{@code zzpatch} [ <var>option</var> ... ] <var>file-or-dir</var> ... <var>existing-dir</var></dt>
     *   <dd>
     *     Transforms <var>file-or-dir</var> ..., creating the output in <var>existing-dir</var>.
     *   </dd>
     * </dl>
     *
     * <h2>Options</h2>
     *
     * <h3>File transformation options:</h3>
     * <dl>
     * {@main.commandLineOptions File-Transformation}
     * </dl>
     *
     * <h3>File transformation conditions:</h3>
     *
     * <p>These control the preceding file transformation.</p>
     *
     * <dl>
     *   <dt>{@code --report} <var>expr</var></dt>
     *   <dd>
     *     Evaluate and print the <var>expr</var> each time the preceding file transformation is executed, e.g.
     *     "{@code path + ": Add '" + name + "' from '" + contentsFile + "'"}". The parameters of the <var>expr</var>
     *     depend on the file transformation (see above).
     *   </dd>
     *   <dt>{@code --iff} <var>expr</var></dt>
     *   <dd>
     *     Execute the preceding file transformation only iff <var>expr</var> evaluates to true. The parameters of the
     *     <var>expr</var> depend on the file transformation (see above).
     *   </dd>
     *   <dt>{@code --mode} {@code REPLACEMENT_STRING|CONSTANT|EXPRESSION} (only with "--substitute")</dt>
     *   <dd>
     *     Determines how the replacement is processed:
     *     <dl>
     *       <dt>{@code REPLACEMENT_STRING}</dt>
     *       <dd>
     *         A JRE <a href="http://docs.oracle.com/javase/7/docs/api/java/util/regex/Matcher.html#appendRepl
     *acement%28java.lang.StringBuffer,%20java.lang.String%29">replacement string</a>
     *       </dd>
     *       <dt>{@code CONSTANT}</dt>
     *       <dd>
     *         A constant string - no character (esp. dollar, backslash) has a special meaning
     *       </dd>
     *       <dt>{@code EXPRESSION}</dt>
     *       <dd>
     *         A <a href="http://commons.unkrig.de/commons-text/apidocs/de/unkrig/commons/text/pattern/Expressio
     *nMatchReplacer.html#parse-java.lang.String-">Java-like expression</a>
     *       </dd>
     *     </dl>
     *   </dd>
     * </dl>
     *
     * <h3>File processing options:</h3>
     * <dl>
     * {@main.commandLineOptions File-Processing}
     * </dl>
     *
     * <h3>General options:</h3>
     * <dl>
     * {@main.commandLineOptions}
     * </dl>
     *
     * <h2>Globs</h2>
     *
     * <p>
     *   Check the descriptions of <a
     *   href="http://commons.unkrig.de/javadoc/de/unkrig/commons/text/pattern/Pattern2.html#WILDCARD">wildcards</a>,
     *   <a
     *   href="http://commons.unkrig.de/javadoc/de/unkrig/commons/text/pattern/Glob.html#INCLUDES_EXCLUDES">includes /
     *   excludes</a> and <a
     *   href="http://commons.unkrig.de/javadoc/de/unkrig/commons/text/pattern/Glob.html#REPLACEMENT">replacements</a>.
     * </p>
     * <p>Examples:</p>
     * <dl>
     *   <dt>{@code dir/file}</dt>
     *   <dd>
     *     File "file" in directory "dir"
     *   </dd>
     *   <dt>{@code file.gz%}</dt>
     *   <dd>
     *     Compressed file "file.gz"
     *   </dd>
     *   <dt>{@code file.zip!dir/file}</dt>
     *   <dd>
     *     Entry "dir/file" in archive file "dir/file.zip"
     *   </dd>
     *   <dt>{@code file.tar.gz%!dir/file}</dt>
     *   <dd>
     *     Entry "{@code dir/file}" in the compressed archive file "{@code file.tar.gz}"
     *   </dd>
     *   <dt><code>&#42;/x</code></dt>
     *   <dd>
     *     File "{@code x}" in an immediate subdirectory
     *   </dd>
     *   <dt><code>*&#42;/x</code></dt>
     *   <dd>
     *     File "{@code x}" in any subdirectory
     *   </dd>
     *   <dt><code>**&#42;/x</code></dt>
     *   <dd>
     *     File "{@code x}" in any subdirectory, or any entry "<code>*&#42;/x</code>" in any archive file in any
     *     subdirectory
     *   </dd>
     *   <dt>{@code a,dir/file.7z!dir/b}</dt>
     *   <dd>
     *     File "{@code a}" and entry "{@code dir/b}" in archive file "{@code dir/file.7z}"
     *   </dd>
     *   <dt>{@code ~*.c}</dt>
     *   <dd>
     *     Files that don't end with "{@code .c}"
     *   </dd>
     *   <dt>{@code ~*.c~*.h}</dt>
     *   <dd>
     *     Files that don't end with "{@code .c}" or "{@code .h}"
     *   </dd>
     *   <dt>{@code ~*.c~*.h,foo.c}</dt>
     *   <dd>
     *     "{@code foo.c}" plus all files that don't end with "{@code .c}" or "{@code .h}"
     *   </dd>
     * </dl>
     *
     * <h2>Regular expressions and replacements</h2>
     *
     * <p>
     *   For the precise description of the supported regular-expression constructs, see <a
     *   href="http://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html#sum">here</a>.
     * </p>
     * <p>
     *   For the precise description of the replacement format, see <a
     *   href="http://docs.oracle.com/javase/7/docs/api/java/util/regex/Matcher.html#appendReplacement%28java.lang.Stri
     *ngBuffer,%20java.lang.String%29">here</a>.
     * </p>
     *
     * <h2>Expressions</h2>
     *
     * <p>
     *   For the precise description of the supported expression constructs, see <a
     *   href="http://commons.unkrig.de/javadoc/index.html?de/unkrig/commons/text/expression/Parser.html">here</a>.
     * </p>
     */
    public static void
    main(String[] args) {

        final Main main = new Main();

        final String[] args2;
        try {
            args2 = CommandLineOptions.parse(args, main);
        } catch (CommandLineOptionException cloe) {
            Printers.error(cloe.getMessage() + ", try \"--help\".");
            System.exit(1);
            return;
        }

        main.levelFilteredPrinter.run(new Runnable() { @Override public void run() { main.main2(args2); } });
    }

    private void
    main2(String[] args) {
        try {
            this.main3(args);
        } catch (Exception e) {
            if (e == FileTransformer.NOT_IDENTICAL) {
                System.exit(1);
            }
            Printers.error(null, e);
        }
    }

    // ================ CONFIGURATION FIELDS ================

    private final LevelFilteredPrinter levelFilteredPrinter = new LevelFilteredPrinter();
    private FileTransformer.Mode       mode                 = FileTransformer.Mode.TRANSFORM;
    private final Patch                patch                = new Patch();
    private Charset                    inputCharset         = Charset.defaultCharset();
    private Charset                    outputCharset        = Charset.defaultCharset();
    private Charset                    patchFileCharset     = Charset.defaultCharset();

    { this.patch.setExceptionHandler(Main.RETHROW); }

    // ================ COMMAND LINE OPTION SETTERS AND ADDERS ================

    /**
     * Look into compressed and archive contents if the format and the path match the glob. The default is to look into
     * any recognised archive or compressed contents.
     * <br />
     * Supported archive formats in this runtime configuration are:
     * <br />
     * {@code ${archive.formats}}
     * <br />
     * Supported compression formats in this runtime configuration are:
     * <br />
     * {@code ${compression.formats}}
     *
     * @param glob                <var>format-glob</var>{@code :}<var>path-glob</var>
     * @main.commandLineOptionGroup File-Processing
     */
    @CommandLineOption public void
    lookInto(@RegexFlags(Pattern2.WILDCARD | Glob.INCLUDES_EXCLUDES) Glob glob) {
        this.patch.setLookIntoFormat(glob);
    }

    /**
     * By default directory members are processed in lexicographical sequence to achieve deterministic results.
     *
     * @main.commandLineOptionGroup File-Processing
     */
    @CommandLineOption public void
    dontSortDirectoryMembers() {  this.patch.setDirectoryMemberNameComparator(null); }

    /**
     * Encoding of patch files (only relevant for "{@code --patch}"); the default is "{@code ${file.encoding}}".
     *
     * @main.commandLineOptionGroup File-Processing
     */
    @CommandLineOption public void
    setPatchFileEncoding(Charset charset) { this.patchFileCharset = charset; }

    @CommandLineOptionGroup
    interface FileTransformerModeCommandLineOptionGroup {}

    /**
     * Do not create or modify any files; exit with status 1 if this is an in-place transformation, and at least one of
     * the files would be changed.
     * <p>
     *   "{@code --check --verbose}" also prints the path of the <em>first</em> file or archive entry that would be
     *   changed.
     * </p>
     * <p>
     *   "{@code --check --verbose --keep-going}" prints the pathes of <em>all</em> files and archive entries that
     *   would be changed.
     * </p>
     *
     * @main.commandLineOptionGroup File-Processing
     */
    @CommandLineOption(group = FileTransformerModeCommandLineOptionGroup.class) public void
    check() { this.mode = FileTransformer.Mode.CHECK; }

    /**
     * Before modifying a file, check whether the change is redundant, i.e. yields an identical result. Could improve
     * performance if few or no files are actually modified.
     *
     * @main.commandLineOptionGroup File-Processing
     */
    @CommandLineOption(group = FileTransformerModeCommandLineOptionGroup.class)
    public void
    checkBeforeTransformation() { this.mode = FileTransformer.Mode.CHECK_AND_TRANSFORM; }

    /**
     * If existing files would be overwritten, keep copies of the originals.
     *
     * @main.commandLineOptionGroup File-Processing
     */
    @CommandLineOption public void
    keep() {  this.patch.setKeepOriginals(true); }

    /**
     * Replace the contents of files/archive entries that match <var>glob</var> (see below) with that of the
     * <var>update-file</var>.
     *
     * @param specification       <var>glob</var>{@code =}<var>update-file</var>
     * @param updateConditions    [ <var>condition</var> ... ]
     * @main.commandLineOptionGroup File-Transformation
     */
    @CommandLineOption(cardinality = CommandLineOption.Cardinality.ANY) public void
    addUpdate(
        @RegexFlags(Pattern2.WILDCARD | Glob.INCLUDES_EXCLUDES | Glob.REPLACEMENT) Glob specification,
        UpdateConditions                                                                updateConditions
    ) {

        ContentsTransformer contentsTransformer = new UpdateContentsTransformer(specification);

        this.patch.addContentsTransformation(
            PredicateUtil.and(specification, ExpressionUtil.toPredicate(updateConditions.result, "path")),
            contentsTransformer
        );
    }

    /**
     * Replace all matches of <var>pattern</var> in files/archive entries that match <var>glob</var> (see below) with
     * the <var>replacement</var> string. "{@code $0}", "{@code $1}", "{@code $2}", etc. expand to the captured groups
     * of the match.
     * <p>
     *   Conditions (see below) apply per match; the parameters of the "{@code --report}" and "{@code --iff}"
     *   conditions are:
     * </p>
     * <dl>
     *   <dt><var>path</var></dt>
     *   <dd>The "path" of the file or ZIP entry that contains the match</dd>
     *   <dt><var>match</var></dt>
     *   <dd>The matching text</dd>
     *   <dt><var>occurrence</var></dt>
     *   <dd>The index of the occurrence within the document, starting at zero</dd>
     * </dl>
     *
     * @param substituteConditions  [ <var>condition</var> ... ]
     * @main.commandLineOptionGroup File-Transformation
     */
    @CommandLineOption(cardinality = CommandLineOption.Cardinality.ANY) public void
    addSubstitute(
        @RegexFlags(Pattern2.WILDCARD | Glob.INCLUDES_EXCLUDES) Glob glob,
        @RegexFlags(Pattern.MULTILINE) Pattern                       pattern,
        String                                                       replacement,
        SubstituteConditions                                         substituteConditions
    ) throws ParseException {
        final Expression condition = substituteConditions.result;

        ContentsTransformer contentsTransformer = new SubstitutionContentsTransformer(
            this.inputCharset,                    // inputCharset
            this.outputCharset,                   // outputCharset
            pattern,                              // pattern
            substituteConditions.replacementMode, // replacementMode
            replacement,                          // replacement
            (                                     // condition
                condition == Expression.TRUE  ? SubstitutionContentsTransformer.Condition.ALWAYS :
                condition == Expression.FALSE ? SubstitutionContentsTransformer.Condition.NEVER  :
                new SubstitutionContentsTransformer.Condition() {

                    @Override public boolean
                    evaluate(String path, CharSequence match, int occurrence) {
                        try {
                            return ExpressionEvaluator.toBoolean(condition.evaluate(
                                Mappings.<String, Object>mapping(
                                    "path",       path, // SUPPRESS CHECKSTYLE Wrap:2
                                    "match",      match,
                                    "occurrence", occurrence
                                )
                            ));
                        } catch (EvaluationException ee) {
                            throw new RuntimeException(ee);
                        }
                    }

                    @Override public String
                    toString() { return condition.toString(); }
                }
            )
        );

        this.patch.addContentsTransformation(glob, contentsTransformer);
    }

    /**
     * Apply <var>patch-file</var> to all files/archive entries that match <var>glob</var> (see below).
     * <var>patch-file</var> can be in traditional, context or unified diff format.
     * <p>
     *   Conditions (see below) apply per match; the parameters of the "{@code --report}" and "{@code --iff}"
     *   conditions are:
     * </p>
     * <dl>
     *   <dt><var>path</var></dt>
     *   <dd>The "path" of the file or ZIP entry being patched</dd>
     *   <dt><var>hunks</var></dt>
     *   <dd>The hunks being applied</dd>
     *   <dt><var>hunkIndex</var></dt>
     *   <dd>The index of the current hunk</dd>
     *   <dt><var>lineNumber</var></dt>
     *   <dd>The line number where the hunk starts</dd>
     * </dl>
     *
     * @param patchConditions             [ <var>condition</var> ... ]
     * @throws UnexpectedElementException The {@code patchFile} does not contain a valid DIFF document
     * @main.commandLineOptionGroup         File-Transformation
     */
    @CommandLineOption(cardinality = CommandLineOption.Cardinality.ANY) public void
    addPatch(
        @RegexFlags(Pattern2.WILDCARD | Glob.INCLUDES_EXCLUDES) Glob glob,
        File                                                         patchFile,
        final PatchConditions                                        patchConditions
    ) throws IOException, UnexpectedElementException {

        ContentsTransformer contentsTransformer = new PatchContentsTransformer(
            this.inputCharset,
            this.outputCharset,
            patchFile,
            this.patchFileCharset,
            new PatchContentsTransformer.Condition() {

                @Override public boolean
                evaluate(
                    String     path,
                    List<Hunk> hunks,
                    int        hunkIndex,
                    Hunk       hunk,
                    int        lineNumber
                ) {
                    try {
                        return ExpressionEvaluator.toBoolean(
                            patchConditions.result.evaluate(Mappings.<String, Object>mapping(
                                "path",       path,      // SUPPRESS CHECKSTYLE Wrap:4
                                "hunks",      hunks,
                                "hunkIndex",  hunkIndex,
                                "lineNumber", lineNumber
                            ))
                        );
                    } catch (EvaluationException ee) {
                        throw new RuntimeException(ee);
                    }
                }
            }
        );

        this.patch.addContentsTransformation(glob, contentsTransformer);
    }

    /**
     * Remove all files/archive entries that match <var>glob</var> (see below).
     *
     * @param removeConditions    [ <var>condition</var> ... ]
     * @main.commandLineOptionGroup File-Transformation
     */
    @CommandLineOption(cardinality = CommandLineOption.Cardinality.ANY) public void
    addRemove(
        @RegexFlags(Pattern2.WILDCARD | Glob.INCLUDES_EXCLUDES) Glob glob,
        RemoveConditions                                             removeConditions
    ) {
        this.patch.addRemoval(PredicateUtil.and(glob, ExpressionUtil.toPredicate(removeConditions.result, "path")));
    }

    /**
     * Rename files/archive entries according to <var>glob</var> (see below), e.g. "{@code (*).c=$1.c.orig}".
     * Multiple "{@code --rename}" options are applied in the given order.
     *
     * @param renameConditions    [ <var>condition</var> ... ]
     * @main.commandLineOptionGroup File-Transformation
     */
    @CommandLineOption(cardinality = CommandLineOption.Cardinality.ANY) public void
    addRename(
        @RegexFlags(Pattern2.WILDCARD | Glob.INCLUDES_EXCLUDES | Glob.REPLACEMENT) final Glob glob,
        final RenameConditions                                                                renameConditions
    ) {

        this.patch.addRenaming(new Glob() {

            @Override public boolean
            matches(String subject) { return glob.matches(subject); }

            @Override @Nullable public String
            replace(String subject) {
                String result = glob.replace(subject);

                return result != null && ExpressionUtil.<String>toPredicate(
                    renameConditions.result,
                    "path"
                ).evaluate(subject) ? result : null;
            }
        });
    }

    /**
     * To all directories and archives that match <var>glob</var>, add a member/entry <var>name</var>, and fill it
     * from <var>contents-file</var>.
     *
     * @param addConditions       [ <var>condition</var> ... ]
     * @main.commandLineOptionGroup File-Transformation
     */
    @CommandLineOption(cardinality = CommandLineOption.Cardinality.ANY) public void
    addAdd(
        @RegexFlags(Pattern2.WILDCARD | Glob.INCLUDES_EXCLUDES) Glob glob,
        String                                                       name,
        File                                                         contentsFile,
        AddConditions                                                addConditions
    ) {

        this.patch.addAddition(PredicateUtil.and(glob, ExpressionUtil.<String>toPredicate(
            addConditions.result,
            "path"
        )), name, contentsFile);
    }

    /**
     * Encoding of input files (only relevant for "{@code --substitute}" and "{@code --patch}"); the default is "{@code
     * ${file.encoding}}".
     *
     * @main.commandLineOptionGroup File-Processing
     */
    @CommandLineOption public void
    setInputEncoding(Charset charset) { this.inputCharset = charset; }

    /**
     * Encoding of output files (only relevant for "{@code --substitute}" and "{@code --patch}"); the default is "{@code
     * ${file.encoding}}".
     *
     * @main.commandLineOptionGroup File-Processing
     */
    @CommandLineOption public void
    setOutputEncoding(Charset charset) { this.outputCharset = charset; }

    /**
     * All of "--patch-file-encoding", "--input-encoding" and "--output-encoding".
     *
     * @main.commandLineOptionGroup File-Processing
     */
    @CommandLineOption public void
    setEncoding(Charset charset) {
        this.patchFileCharset = charset;
        this.inputCharset     = charset;
        this.outputCharset    = charset;
    }

    /**
     * Print this text and terminate.
     */
    @CommandLineOption public static void
    help() throws IOException {

        System.setProperty("archive.formats",     ArchiveFormatFactory.allFormats().toString());
        System.setProperty("compression.formats", CompressionFormatFactory.allFormats().toString());
        CommandLineOptions.printResource(Main.class, "main(String[]).txt", Charset.forName("UTF-8"), System.out);

        System.exit(0);
    }

    /**
     * Print error and continue with next file.
     */
    @CommandLineOption public void
    keepGoing() { this.patch.setExceptionHandler(Main.PRINT_AND_CONTINUE); }

    /**
     * Suppress all messages except errors.
     */
    @CommandLineOption public void
    noWarn() { this.levelFilteredPrinter.setNoWarn(); }

    /**
     * Suppress normal output.
     */
    @CommandLineOption public void
    quiet() { this.levelFilteredPrinter.setQuiet(); }

    /**
     * Print verbose messages.
     */
    @CommandLineOption public void
    verbose() { this.levelFilteredPrinter.setVerbose(); }

    /**
     * Print verbose and debug messages.
     */
    @CommandLineOption public void
    debug() { this.levelFilteredPrinter.setDebug(); }

    /**
     * Add logging at level {@code FINE} on logger "{@code de.unkrig}" to STDERR using the "{@code FormatFormatter}"
     * and "{@code SIMPLE}" format, or the given arguments, which are all optional.
     *
     * @param spec <var>level</var>{@code :}<var>logger</var>{@code :}<var>handler</var>{@code
     *             :}<var>formatter</var>{@code :}<var>format</var>
     */
    @CommandLineOption(cardinality = CommandLineOption.Cardinality.ANY) public static void
    addLog(String spec) { SimpleLogging.configureLoggers(spec); }

    private void
    main3(String[] args) throws IOException {

        if (args.length == 0) {

            if (this.mode != FileTransformer.Mode.TRANSFORM) {
                System.err.println(
                    "\"--check\" or \"--check-before-transformation\" cannot be used if the input is STDIN."
                );
                System.exit(1);
            }

            this.patch.contentsTransformer().transform("-", System.in, System.out);
        } else {

            FileTransformations.transform(
                args,
                this.patch.fileTransformer(
                    true, // lookIntoDirectories
                    false // renameOrRemoveTopLevelFiles
                ),
                this.mode,
                this.patch.getExceptionHandler()
            );
        }
    }

    /**
     * This bean represents the various "conditions" command line options of the "--substitute" action.
     */
    public static
    class SubstituteConditions extends Conditions {

        public Mode replacementMode = Mode.REPLACEMENT_STRING;

		public SubstituteConditions() { super("path", "match", "occurrence"); }

        /**
         * Evaluate and print the <var>expression</var> each time the preceding file transformation is executed, e.g.
         * "{@code path + ": Add '" + name + "' from '" + contentsFile + "'"}". The parameters of the
         * <var>expression</var> depend on the file transformation (see above).
         */
        @CommandLineOption public void
        addMode(SubstitutionContentsTransformer.Mode replacementMode) {
        	this.replacementMode = replacementMode;
        }
    }

    /**
     * This bean represents the various "conditions" command line options of the "--patch" action.
     */
    public static
    class PatchConditions extends Conditions {

        public PatchConditions() { super("path", "hunks", "hunkIndex", "hunk", "lineNumber"); }
    }

    /**
     * This bean represents the various "conditions" command line options of the "--remove" action.
     */
    public static
    class RemoveConditions extends Conditions {

        public RemoveConditions() { super("path"); }
    }

    /**
     * This bean represents the various "conditions" command line options of the "--update" action.
     */
    public static
    class UpdateConditions extends Conditions {

        public UpdateConditions() { super("path"); }
    }

    /**
     * This bean represents the various "conditions" command line options of the "--rename" action.
     */
    public static
    class RenameConditions extends Conditions {

        public RenameConditions() { super("path"); }
    }

    /**
     * This bean represents the various "conditions" command line options of the "--add" action.
     */
    public static
    class AddConditions extends Conditions {

        public AddConditions() { super("path"); }
    }

    /**
     * The base class of the various "conditions" command line options beans.
     */
    public abstract static
    class Conditions {

        private final String[] variableNames;

        /**
         * The expression that implents the "{@code --iff}"s and "{@code --report}"s. Iff none of these is configured,
         * then the this field is {@link Expression#TRUE}.
         */
        Expression result = Expression.TRUE;

        /**
         * @param variableNames The names of the variables that expressions can reference
         */
        public
        Conditions(String... variableNames) { this.variableNames = variableNames; }

        /**
         * Evaluate and print the <var>expression</var> each time the preceding file transformation is executed, e.g.
         * "{@code path + ": Add '" + name + "' from '" + contentsFile + "'"}". The parameters of the
         * <var>expression</var> depend on the file transformation (see above).
         */
        @CommandLineOption public void
        addReport(String expression) throws ParseException {

            final Expression reportExpression = new ExpressionEvaluator(this.variableNames).parse(expression);

            this.result = ExpressionUtil.logicalAnd(this.result, new AbstractExpression() {

                @Override @Nullable public Object
                evaluate(Mapping<String, ?> variables) throws EvaluationException {

                    Printers.info(String.valueOf(reportExpression.evaluate(variables)));

                    return true;
                }
            });
        }

        /**
         * Execute the preceding file transformation only iff <var>expression</var> evaluates to true. The parameters
         * of the <var>expression</var> depend on the file transformation (see above).
         */
        @CommandLineOption public void
        addIff(String expression) throws ParseException {

            final Expression iffExpression = new ExpressionEvaluator(this.variableNames).parse(expression);

            this.result = ExpressionUtil.logicalAnd(this.result, iffExpression);
        }
    }
}
