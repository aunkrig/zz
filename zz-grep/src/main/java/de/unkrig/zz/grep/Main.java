
/*
 * de.unkrig.grep - An advanced version of the UNIX GREP utility
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

package de.unkrig.zz.grep;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import de.unkrig.commons.file.ExceptionHandler;
import de.unkrig.commons.file.fileprocessing.FileProcessings;
import de.unkrig.commons.file.org.apache.commons.compress.archivers.ArchiveFormatFactory;
import de.unkrig.commons.file.org.apache.commons.compress.compressors.CompressionFormatFactory;
import de.unkrig.commons.lang.AssertionUtil;
import de.unkrig.commons.lang.protocol.ProducerWhichThrows;
import de.unkrig.commons.nullanalysis.Nullable;
import de.unkrig.commons.text.LevelFilteredPrinter;
import de.unkrig.commons.text.Printers;
import de.unkrig.commons.text.pattern.Glob;
import de.unkrig.commons.text.pattern.IncludeExclude;
import de.unkrig.commons.text.pattern.Pattern2;
import de.unkrig.commons.util.CommandLineOptionException;
import de.unkrig.commons.util.CommandLineOptions;
import de.unkrig.commons.util.annotation.CommandLineOption;
import de.unkrig.commons.util.annotation.RegexFlags;
import de.unkrig.commons.util.logging.SimpleLogging;
import de.unkrig.zz.grep.Grep.Operation;

/**
 * A GREP utility that looks recursively into directories, archive files and compressed files
 */
public final
class Main {

    static { AssertionUtil.enableAssertionsForThisClass(); }

    private Main() {}

    private final Grep grep = new Grep();
    {
        this.grep.setExceptionHandler(new ExceptionHandler<IOException>() {
            @Override public void handle(String path, IOException ioe)     { Printers.error(path, ioe); }
            @Override public void handle(String path, RuntimeException re) { Printers.error(path, re); }
        });
    }
    private boolean              caseSensitive  = true;
    private final IncludeExclude includeExclude = new IncludeExclude();

    private final LevelFilteredPrinter levelFilteredPrinter = new LevelFilteredPrinter();

    /**
     * <h2>Usage:</h2>
     * <dl>
     *   <dt>{@code zzgrep} [ <var>option</var> ... ] [ {@code -} ] <var>regex</var> <var>file-or-dir</var> ...</dt>
     *   <dd>
     *     Reads <var>file</var> or all files under <var>dir</var> and prints all lines which contain matches of the
     *     <var>regex</var>.
     *   </dd>
     *   <dt>{@code zzgrep} [ <var>option</var> ... ] <var>regex</var></dt>
     *   <dd>
     *     Reads STDIN and prints all lines which contain matches of the <var>regex</var>.
     *   </dd>
     * </dl>
     *
     * <h2>Options:</h2>
     *
     * <dl>
     * {@main.commandLineOptions}
     * </dl>
     *
     * <h3>File Selection:</h3>
     * <dl>
     * {@main.commandLineOptions File-Selection}
     * </dl>
     *
     * <h3>Contents Processing:</h3>
     * <dl>
     * {@main.commandLineOptions Contents-Processing}
     * </dl>
     *
     * <h3>Output Generation:</h3>
     * <dl>
     * {@main.commandLineOptions Output-Generation}
     * </dl>
     *
     * <h3>Example <var>glob</var>s:</h3>
     * <dl>
     *   <dt>{@code  dir/file}</dt>
     *   <dd>
     *     File "{@code file}" in directory "{@code dir}"
     *   </dd>
     *   <dt>{@code file.gz%}</dt>
     *   <dd>
     *     Compressed file "{@code file.gz}"
     *   </dd>
     *   <dt>{@code file.zip!dir/file}</dt>
     *   <dd>
     *     Entry "{@code dir/file}" in archive file "{@code dir/file.zip}"
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
     * <p>
     *   Exit status is 0 if any line was selected, 1 otherwise; if any error occurs, the exit status is 2.
     * </p>
     */
    public static void
    main(final String[] args) {

        new Main().levelFilteredPrinter.run(new Runnable() {
            @Override public void run() { new Main().main2(args); }
        });
    }

    private void
    main2(String[] args) {

        try {
            this.main3(args);
        } catch (Exception e) {
            Printers.error(null, e);
            System.exit(1);
        }
    }

    private void
    main3(String[] args) throws IOException, InterruptedException {

        try {
            args = CommandLineOptions.parse(args, this);
        } catch (CommandLineOptionException cloe) {
            Printers.error(cloe.getMessage() + ", try \"--help\".");
            System.exit(1);
        }

        // Process pattern command line argument.
        if (args.length == 0) {
            System.err.println("Pattern missing, try \"--help\".");
            System.exit(1);
        }

        this.grep.addSearch(this.includeExclude, args[0], this.caseSensitive);

        // Process files command line arguments.
        final List<File> files = new ArrayList<File>();
        for (int i = 1; i < args.length; i++) files.add(new File(args[i]));

        if (files.isEmpty()) {
            this.grep.contentsProcessor().process(
                "",                                                   // path
                System.in,                                            // inputStream
                -1L,                                                  // size
                -1L,                                                  // crc32
                new ProducerWhichThrows<InputStream, IOException>() { // opener
                    @Override @Nullable public InputStream produce() { throw  new UnsupportedOperationException(); }
                }
            );
        } else {
            FileProcessings.process(files, this.grep.fileProcessor(true));
        }

        if (!this.grep.linesSelected) System.exit(1);
    }

    /**
     * Print this text and terminate.
     */
    @CommandLineOption public static void
    help() throws IOException {

        System.setProperty("archive.formats",     ArchiveFormatFactory.allFormats().toString());
        System.setProperty("compression.formats", CompressionFormatFactory.allFormats().toString());
        CommandLineOptions.printResource(
            Main.class,
            "main(String[]).txt",
            Charset.forName("UTF-8"),
            System.out
        );

        System.exit(0);
    }

    /**
     * By default directory members are sorted lexicographically in order to achieve deterministic results.
     *
     * @main.commandLineOptionGroup File-Selection
     */
    @CommandLineOption public void
    dontSortDirectoryMembers() { this.grep.setDirectoryMemberNameComparator(null); }

    /**
     * Look into compressed and archive contents if "<var>format</var>:<var>path</var>" matches the glob.
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
     */
    @CommandLineOption public void
    lookInto(@RegexFlags(Pattern2.WILDCARD | Glob.INCLUDES_EXCLUDES) Glob glob) { this.grep.setLookIntoFormat(glob); }

    /**
     * Contents encoding, default is "${file.encoding}".
     *
     * @main.commandLineOptionGroup Contents-Processing
     */
    @CommandLineOption public void
    setEncoding(Charset charset) { this.grep.setCharset(charset); }

    /**
     * Print only names of files containing matches.
     *
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption(name = { "l", "list" }) public void
    setList() { this.grep.setOperation(Operation.LIST); }

    /**
     * Ignore case distinctions.
     *
     * @main.commandLineOptionGroup Contents-Processing
     */
    @CommandLineOption(name = { "i", "ignore-case" }) public void
    ignoreCase() { this.caseSensitive = false; }

    /**
     * Select non-matching lines.
     *
     * @main.commandLineOptionGroup Contents-Processing
     */
    @CommandLineOption(name = { "v", "inverted" }) public void
    inverted() { this.grep.setInverted(true); }

    /**
     * Disassemble .class files on-the-fly.
     *
     * @main.commandLineOptionGroup Contents-Processing
     */
    @CommandLineOption(name = { "da", "disassemble-class-files" }) public void
    setDisassemble() { this.grep.setDisassembleClassFiles(true); }

    /**
     * When disassembling .class files, include a constant pool dump, constant pool indexes, and hex dumps of all
     * attributes in the disassembly output.
     *
     * @main.commandLineOptionGroup Contents-Processing
     */
    @CommandLineOption public void
    setDaVerbose() { this.grep.setDisassembleClassFilesVerbose(true); }

    /**
     * When disassembling .class files, look for source files in this directory. Source file loading is disabled by
     * default.
     *
     * @main.commandLineOptionGroup Contents-Processing
     */
    @CommandLineOption public void
    setDaSourceDirectory(File directory) { this.grep.setDisassembleClassFilesSourceDirectory(directory); }

    /**
     * Don't print line numbers in the disassembly.
     *
     * @main.commandLineOptionGroup Contents-Processing
     */
    @CommandLineOption(name = { "da-no-lines", "disassemble-class-files-but-hide-lines" }) public void
    setDisassembleClassFilesButHideLines() { this.grep.setDisassembleClassFilesButHideLines(true); }

    /**
     * Don't print variable names in the disassembly.
     *
     * @main.commandLineOptionGroup Contents-Processing
     */
    @CommandLineOption(name = { "da-no-vars", "disassemble-class-files-but-hide-vars" }) public void
    setDisassembleClassFilesButHideVars() { this.grep.setDisassembleClassFilesButHideVars(true); }

    /**
     * When disassembling .class files, use symbolic labels /'L12') instead of numeric labels ('#123').
     *
     * @main.commandLineOptionGroup Contents-Processing
     */
    @CommandLineOption public void
    setDaSymbolicLabels() { this.grep.setDisassembleClassFilesSymbolicLabels(true); }

    /**
     * Excludes files and archive entries from the search iff the file's / archive entry's path matches the
     * <var>regex</var>.
     * <p>
     *   Any number of {@code --exclude} and {@code --include} options can be given, and later take precedence over
     *   the earlier.
     * </p>
     * <p>
     *   The default is that <em>all</em> files and archive entries are included; thus you would always begin with a
     *   {@code --exclude} option.
     * </p>
     *
     * @main.commandLineOptionGroup File-Selection
     */
    @CommandLineOption(cardinality = CommandLineOption.Cardinality.ANY) public void
    addExclude(@RegexFlags(Pattern2.WILDCARD) Glob regex) { this.includeExclude.addExclude(regex, true); }

    /**
     * @see #addExclude(Glob)
     *
     * @main.commandLineOptionGroup File-Selection
     */
    @CommandLineOption(cardinality = CommandLineOption.Cardinality.ANY) public void
    addInclude(@RegexFlags(Pattern2.WILDCARD) Glob regex) { this.includeExclude.addInclude(regex, true); }

    /**
     * Suppress all normal output.
     *
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption(name = { "q", "quiet" }) public void
    setQuiet() { this.grep.setOperation(Operation.QUIET); }

    /**
     * Suppress all messages except errors.
     *
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption public void
    setNowarn() { this.levelFilteredPrinter.setNoWarn(); }

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

    /**
     * Add logging at level {@code FINE} on logger "{@code de.unkrig}" to STDERR using the {@code FormatFormatter}
     * and {@code SIMPLE} format, or the given arguments (which are all optional).
     *
     * @param spec <var>level</var>:<var>logger</var>:<var>handler</var>:<var>formatter</var>:<var>format</var>
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption(cardinality = CommandLineOption.Cardinality.ANY) public static void
    addLog(String spec) { SimpleLogging.configureLoggers(spec); }

    // Deprecated options.

    /**
     * @main.commandLineOptionGroup File-Selection
     * @deprecated Use {@code --look-into} instead.
     */
    @Deprecated @CommandLineOption(name = { "zip", "zz", "nested-zip", "gzip" }) public static void
    noLongerSupported() {
        System.err.println("Command line option is no longer supported - try \"--help\".");
        System.exit(1);
    }
}
