
/*
 * de.unkrig.diff - An advanced version of the UNIX DIFF utility
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

package de.unkrig.zz.diff;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.regex.Pattern;

import de.unkrig.commons.file.ExceptionHandler;
import de.unkrig.commons.file.org.apache.commons.compress.archivers.ArchiveFormatFactory;
import de.unkrig.commons.file.org.apache.commons.compress.compressors.CompressionFormatFactory;
import de.unkrig.commons.lang.AssertionUtil;
import de.unkrig.commons.lang.protocol.RunnableWhichThrows;
import de.unkrig.commons.nullanalysis.Nullable;
import de.unkrig.commons.text.AbstractPrinter;
import de.unkrig.commons.text.AbstractPrinter.Level;
import de.unkrig.commons.text.LevelFilteredPrinter;
import de.unkrig.commons.text.Printers;
import de.unkrig.commons.text.pattern.Glob;
import de.unkrig.commons.text.pattern.Pattern2;
import de.unkrig.commons.util.CommandLineOptions;
import de.unkrig.commons.util.annotation.CommandLineOption;
import de.unkrig.commons.util.annotation.CommandLineOptionGroup;
import de.unkrig.commons.util.annotation.RegexFlags;
import de.unkrig.zz.diff.Diff.AbsentFileMode;
import de.unkrig.zz.diff.Diff.DiffMode;
import de.unkrig.zz.diff.Diff.LineEquivalence;
import de.unkrig.zz.diff.DocumentDiff.Tokenization;

/**
 * A DIFF utility that can recurse into directories, archive files and compressed files.
 */
public
class Main {

    static { AssertionUtil.enableAssertionsForThisClass(); }

    /**
     * <h2>Usage:</h2>
     * <dl>
     *   <dt>{@code zzdiff} [ <var>option</var> ] ... <var>file1</var> <var>file2</var></dt>
     *   <dd>
     *     Show contents differences between <var>file1</var> and <var>file2</var> in DIFF format. The "path"
     *     (relevant, e.g., for the "--path" command line option, see below) is "" (the empty string).
     *   </dd>
     *   <dt>{@code zzdiff} [ <var>option</var> ] ... <var>dir1</var> <var>dir2</var></dt>
     *   <dd>
     *     Show which files were added (missing in <var>dir1</var>) or deleted (missing in <var>dir2</var>) and their
     *     subdirectories, and any contents differences for the remaining files in DIFF format. The "path" (relevant,
     *     e.g., for the "--path" command line option, see below) is relative to <var>dir1</var>, e.g. "file.txt"
     *     or "subdir/file.zip!dir/file.txt" or "archive.tgz%!dir/file.txt" or "dir/file.Z%".
     *   </dd>
     * </dl>
     * <p>
     *   The default output format is the "<a href="http://en.wikipedia.org/wiki/Diff_utility#Usage">normal
     *   format</a>", also known as the "traditional format". Other output formats can be chosen through command
     *   line options, see "Output generation", below.
     * </p>
     *
     * <h2>Options:</h2>
     *
     * <h3>General</h3>
     *
     * <dl>
     * {@main.commandLineOptions}
     * </dl>
     *
     * <h3>File selection</h3>
     *
     * <dl>
     * {@main.commandLineOptions File-Selection}
     * </dl>
     *
     * <h3>Content processing</h3>
     *
     * <dl>
     * {@main.commandLineOptions Contents-Processing}
     * </dl>
     *
     * <h3>Output generation</h3>
     *
     * <dl>
     * {@main.commandLineOptions Output-Generation}
     * </dl>
     *
     * <h2>Globs</h2>
     *
     * <p>
     *   A <var>glob</var> can have the form
     * </p>
     * <pre>
     *   {@code *~*.c~*.h~foo.c}
     * </pre>
     * <p>
     *   , which means "foo.c plus all that don't end with .c or .h".
     * </p>
     *
     * <h3>Example <var>glob</var>s:</h3>
     *
     * <dl>
     *   <dt>{@code dir/file}</dt>
     *   <dd>
     *     File "file" in directory "dir".
     *   </dd>
     *   <dt>{@code file.gz%}</dt>
     *   <dd>
     *     Compressed file "file.gz".
     *   </dd>
     *   <dt>{@code file.zip!dir/file}</dt>
     *   <dd>
     *     Entry "dir/file" in archive file "dir/file.zip".
     *   </dd>
     *   <dt>{@code file.tar.gz%!dir/file}</dt>
     *   <dd>
     *     Entry "dir/file" in the compressed archive file "file.tar.gz".
     *   </dd>
     *   <dt><code>&#42;/x</code></dt>
     *   <dd>
     *     File "x" in an immediate subdirectory.
     *   </dd>
     *   <dt><code>*&#42;/x</code></dt>
     *   <dd>
     *     File "x" in any subdirectory.
     *   </dd>
     *   <dt><code>**&#42;/x</code></dt>
     *   <dd>
     *     File "x" in any subdirectory, or any entry "*&#42;/x" in any archive file in any subdirectory.
     *   </dd>
     *   <dt>{@code a,dir/file.7z!dir/b}</dt>
     *   <dd>
     *     File "a" and entry "dir/b" in archive file "dir/file.7z".
     *   </dd>
     *   <dt>{@code ~*.c}</dt>
     *   <dd>
     *     Files that don't end with ".c".
     *   </dd>
     *   <dt>{@code ~*.c~*.h}</dt>
     *   <dd>
     *     Files that don't end with ".c" or ".h".
     *   </dd>
     *   <dt>{@code ~*.c~*.h,foo.c}</dt>
     *   <dd>
     *     "foo.c" plus all files that don't end with ".c" or ".h".
     *   </dd>
     * </dl>
     */
    public static void
    main(final String[] args) {

        final Main main = new Main();
        main.levelFilteredPrinter.run(new Runnable() {

            @Override public void run() { main.main2(args); }
        });
    }

    private final Diff                 diff                 = new Diff();
    private final LevelFilteredPrinter levelFilteredPrinter = new LevelFilteredPrinter(AbstractPrinter.getContextPrinter());
    @Nullable private File             outputFile;

    public Main() {}

    /**
     * Processes any thrown exceptions.
     */
    private void
    main2(String[] args) {

        try {
            this.main3(args);
        } catch (Exception e) {
            Printers.error(null, e);
            System.exit(1);
        }
    }

    /**
     * Processes the command line options and arguments.
     */
    private void
    main3(String[] args) throws Exception {

        args = CommandLineOptions.parse(args, this);

        if (args.length != 2) {
            Printers.error("Wrong number of file or directory names - try \"--help\".");
            System.exit(2);
        }

        this.main4(new File(args[0]), new File(args[1]));
    }

    /**
     * Handles the {@link #outputFile}.
     */
    private void
    main4(final File file1, final File file2) throws Exception {

        Printers.redirectToFile(
            Level.INFO,                            // level
            this.outputFile,                       // outputFile
            null,                                  // charset
            new RunnableWhichThrows<Exception>() { // runnable
                @Override public void run() throws Exception { Main.this.main5(file1, file2); }
            }
        );
    }

    /**
     * Exits with status "1" iff there are one or more differences.
     */
    private void
    main5(final File file1, final File file2) throws Exception {

        long differenceCount = Main.this.diff.execute(file1, file2);

        if (differenceCount > 0) System.exit(1);
    }

    /**
     * Print this text and terminate.
     */
    @CommandLineOption public void
    help() throws IOException {

        System.setProperty("archive.formats",     ArchiveFormatFactory.allFormats().toString());
        System.setProperty("compression.formats", CompressionFormatFactory.allFormats().toString());
        CommandLineOptions.printResource(Main.class, "main(String[]).txt", Charset.forName("UTF-8"), System.out);

        System.exit(0);
    }

    /**
     * Process only matching files/entries, e.g."{@code dir/file.zip!dir/file}" or "<code>*&#42;/file</code>".
     * See also "Globs", below.
     *
     * @main.commandLineOptionGroup File-Selection
     */
    @CommandLineOption public void
    setPath(@RegexFlags(Pattern2.WILDCARD | Glob.INCLUDES_EXCLUDES) Glob glob) {
        this.diff.setPathPredicate(glob);
    }

    /**
     * Look into compressed and archive contents if its format matches <var>format-glob</var> and its path matches the
     * <var>path-glob</var>.
     * The default is to look into any recognised archive or compressed contents.
     * <br />
     * Supported archive formats in this runtime configuration are:
     * <br />
     * {@code ${archive.formats}}
     * <br />
     * Supported compression formats in this runtime configuration are:
     * <br />
     * {@code ${compression.formats}}
     *
     * @main.commandLineOptionGroup File-Selection
     * @param discriminator       <var>format-glob</var>:<var>path-glob</var>
     */
    @CommandLineOption public void
    setLookInto(@RegexFlags(Glob.INCLUDES_EXCLUDES | Pattern2.WILDCARD) Glob discriminator) {
        this.diff.setLookInto(discriminator);
    }

    /**
     * Files with different names map iff their names match the <var>path-regex</var> and all capturing groups are
     * equal. May be given more than once.
     *
     * @main.commandLineOptionGroup File-Selection
     */
    @CommandLineOption(name = { "path-equivalence", "pe" }, cardinality = CommandLineOption.Cardinality.ANY) public void
    addPathEquivalence(Pattern pathRegex) { this.diff.addEquivalentPath(pathRegex); }

    /**
     * @main.commandLineOptionGroup File-Selection
     * @deprecated                  Equivalent with "{@code --path-equivalence} <var>path-regex</var>".
     */
    @Deprecated @CommandLineOption(name = "ne", cardinality = CommandLineOption.Cardinality.ANY) public void
    addNameEquivalence(Pattern pathRegex) { this.addPathEquivalence(pathRegex); }

    /**
     * Don't recurse through subdirectories; just compare the <b>existence</b> of subdirectories.
     *
     * @main.commandLineOptionGroup File-Selection
     */
    @CommandLineOption public void
    setNoRecurseSubdirctories() { this.diff.setRecurseSubdirectories(false); }

    // CONTENTS PROCESSING OPTIONS

    /**
     * Disassemble .class files.
     *
     * @main.commandLineOptionGroup Contents-Processing
     */
    @CommandLineOption(name = { "disassemble", "da" }) public void
    setDisassemble() { this.diff.setDisassembleClassFiles(true); }

    /**
     * When disassembling .class files, don't display lines debug info.
     *
     * @main.commandLineOptionGroup Contents-Processing
     */
    @CommandLineOption public void
    setDaNoLines() { this.diff.setDisassembleClassFilesButHideLines(true); }

    /**
     * When disassembling .class files, don't display local variables debug info.
     *
     * @main.commandLineOptionGroup Contents-Processing
     */
    @CommandLineOption public void
    setDaNoVars() { this.diff.setDisassembleClassFilesButHideVars(true); }

    /**
     * Lines in files <var>path-pattern</var> that contain <var>line-regex</var> and all capturing groups are equal are
     * regarded as equal.
     *
     * @main.commandLineOptionGroup Contents-Processing
     */
    @CommandLineOption(cardinality = CommandLineOption.Cardinality.ANY) public void
    addLineEquivalence(
        @RegexFlags(Pattern2.WILDCARD | Glob.INCLUDES_EXCLUDES) Glob pathPattern,
        Pattern                                                      lineRegex
    ) { this.diff.addEquivalentLine(new LineEquivalence(pathPattern, lineRegex)); }

    /**
     * Ignore differences in files <var>path-pattern</var> where all lines (deleted, changed or added) match the given
     * <var>line-regex</var>.
     *
     * @main.commandLineOptionGroup Contents-Processing
     */
    @CommandLineOption(name = { "ignore", "I" }, cardinality = CommandLineOption.Cardinality.ANY) public void
    addIgnore(
        @RegexFlags(Pattern2.WILDCARD | Glob.INCLUDES_EXCLUDES) Glob pathPattern,
        Pattern                                                      lineRegex
    ) { this.diff.addIgnore(new LineEquivalence(pathPattern, lineRegex)); }

    /**
     * Ignore whitespace differences.
     *
     * @main.commandLineOptionGroup Contents-Processing
     */
    @CommandLineOption(name = { "ignore-whitespace", "w" }) public void
    setIgnoreWhitespace() { this.diff.setIgnoreWhitespace(true); }

    @CommandLineOptionGroup interface AddedFileModeOptionGroup {}
    @CommandLineOptionGroup interface DeletedFileModeOptionGroup {}

    /**
     * How to deal with added resp. deleted files:
     * <dl>
     *   <dt>REPORT (the default):</dt>
     *   <dd>
     *     Print "File added <var>path</var>", resp. "File deleted <var>path</var>" (or, with "--exist" or "--brief",
     *     print "+ <var>path</var>" resp. "- <var>path</var>").
     *   </dd>
     *   <dt>COMPARE_WITH_EMPTY:</dt>
     *   <dd>
     *     Generate a diff document by comparing with the empty document.
     *   </dd>
     *   <dt>IGNORE:</dt>
     *   <dd>
     *     Do nothing.
     *   </dd>
     * </dl>
     *
     * @main.commandLineOptionGroup Contents-Processing
     */
    @CommandLineOption(group = AddedFileModeOptionGroup.class) public void
    setAddedFile(AbsentFileMode mode) { this.diff.setAddedFileMode(mode); }

    /**
     * @see #setAddedFile(Diff.AbsentFileMode)
     */
    @CommandLineOption(group = DeletedFileModeOptionGroup.class) public void
    setDeletedFile(AbsentFileMode mode) { this.diff.setDeletedFileMode(mode); }

    /**
     * Treat absent files as empty. This is shorthand for "{@code --added-file COMPARE_WITH_EMPTY --deleted-file
     * COMPARE_WITH_EMPTY}".
     *
     * @main.commandLineOptionGroup Contents-Processing
     */
    @CommandLineOption(
        name = { "new-file", "N" },
        group = { AddedFileModeOptionGroup.class, DeletedFileModeOptionGroup.class }
    ) public void
    setNewFile() {
        this.diff.setAddedFileMode(AbsentFileMode.COMPARE_WITH_EMPTY);
        this.diff.setDeletedFileMode(AbsentFileMode.COMPARE_WITH_EMPTY);
    }

    /**
     * Treat added files as (previously) empty; deleted files are only reported. This is shorthand for "{@code
     * --added-file COMPARE_WITH_EMPTY --deleted-file REPORT}".
     *
     * @main.commandLineOptionGroup Contents-Processing
     */
    @CommandLineOption(group = { AddedFileModeOptionGroup.class, DeletedFileModeOptionGroup.class }) public void
    setUnidirectionalNewFile() {
        this.diff.setAddedFileMode(AbsentFileMode.COMPARE_WITH_EMPTY);
        this.diff.setDeletedFileMode(AbsentFileMode.REPORT);
    }

    /**
     * Also report unchanged files.
     *
     * @main.commandLineOptionGroup Contents-Processing
     */
    @CommandLineOption public void
    setUnchangedFiles() { this.diff.setReportUnchangedFiles(true); }

    /**
     * Encoding of the files being compared (defaults to the JVM default charset, "${file.encoding}").
     *
     * @main.commandLineOptionGroup Contents-Processing
     */
    @CommandLineOption public void
    setEncoding(Charset charsetName) { this.diff.setCharset(charsetName); }

    // OUTPUT GENERATION OPTIONS

    /**
     * Write DIFF to <var>output-file</var> instead of STDOUT.
     *
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption public void
    setOut(File outputFile) { this.outputFile = outputFile; }

    @CommandLineOptionGroup
    interface DiffModeOptionGroup {}

    /**
     * Output "<a href="https://en.wikipedia.org/wiki/Diff_utility#Usage">normal diff format</a>"; this is the default.
     *
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption(group = DiffModeOptionGroup.class) public void
    setNormal() { this.diff.setDiffMode(DiffMode.NORMAL); }

    /**
     * Report only which files were added or deleted (do <em>not</em> report changed content).
     *
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption(group = DiffModeOptionGroup.class) public void
    setExist() { this.diff.setDiffMode(DiffMode.EXIST); }

    /**
     * Report only which files were added, deleted or changed.
     *
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption(name = { "brief", "q" }, group = DiffModeOptionGroup.class) public void
    setBrief() { this.diff.setDiffMode(DiffMode.BRIEF); }

    /**
     * Output "<a href="http://en.wikipedia.org/wiki/Diff_utility#Context_format">context diff format</a>" with three
     * lines of context.
     *
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption(name = { "context", "c" }, group = DiffModeOptionGroup.class) public void
    setContext() { this.diff.setDiffMode(DiffMode.CONTEXT); }

    /**
     * Output "<a href="http://en.wikipedia.org/wiki/Diff_utility#Context_format">context diff format</a>" with
     * <var>amount</var> lines of context.
     *
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption(name = { "Context", "C" }, group = DiffModeOptionGroup.class) public void
    setContext2(int amount) {
        this.diff.setDiffMode(DiffMode.CONTEXT);
        this.diff.setContextSize(amount);
    }

    /**
     * Output "<a href="http://en.wikipedia.org/wiki/Diff_utility#Unified_format">unified diff format</a>" with three
     * lines of context.
     *
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption(name = { "unified", "u" }, group = DiffModeOptionGroup.class) public void
    setUnified() { this.diff.setDiffMode(DiffMode.UNIFIED); }

    /**
     * Output "<a href="http://en.wikipedia.org/wiki/Diff_utility#Unified_format">unified diff format</a>" with
     * <var>amount</var> lines of context.
     *
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption(name = "Unified", group = DiffModeOptionGroup.class) public void
    setUnified2(int amount) {
        this.diff.setDiffMode(DiffMode.UNIFIED);
        this.diff.setContextSize(amount);
    }

    /**
     * Report errors and continue with next file.
     *
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption public void
    setKeepGoing() {

        this.diff.setExceptionHandler(new ExceptionHandler<IOException>() {
            @Override public void handle(String path, IOException ioe)     { Printers.error(path, ioe.getMessage()); }
            @Override public void handle(String path, RuntimeException re) { Printers.error(path, re.getMessage()); }
        });
    }

    /**
     * Scan directories strictly sequentially. The default is to parallelize the directory scan in multiple threads.
     *
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption public void
    setSequential() { this.diff.setSequential(true); }

    /**
     * Regard documents as streams of Java tokens; the whitespace between tokens (including line breaks) is then
     * insignificant.
     *
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption public void
    setJavaTokenization() { this.diff.setTokenization(Tokenization.JAVA); }

    /**
     * Don't regard C-style comments ("<code>/&#42; ... &#42;/</code>") as relevant for comparison.
     *
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption public void
    setIgnoreCStyleComments() { this.diff.setIgnoreCStyleComments(true); }

    /**
     * Don't regard C++-style comments ("<code>// ...</code>") as relevant for comparison.
     *
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption(name = "ignore-c++-style-comments") public void
    setIgnoreCppStyleComments() { this.diff.setIgnoreCPlusPlusStyleComments(true); }

    /**
     * Don't regard doc comments ("<code>/** ... &#42;/</code>") as relevant for comparison.
     *
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption public void
    setIgnoreDocComments() { this.diff.setIgnoreDocComments(true); }

    /**
     * Suppress all output except errors.
     *
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption public void
    setNoWarn() { this.levelFilteredPrinter.setNoWarn(); }

    /**
     * Suppress "normal" output; print only errors and warnings.
     *
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption public void
    setQuiet() { this.levelFilteredPrinter.setQuiet(); }

    /**
     * Print verbose messages.
     *
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption public void
    setVerbose() { this.levelFilteredPrinter.setVerbose(); }

    /**
     * Print verbose and debug messages.
     *
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption public void
    setDebug() { this.levelFilteredPrinter.setDebug(); }
}
