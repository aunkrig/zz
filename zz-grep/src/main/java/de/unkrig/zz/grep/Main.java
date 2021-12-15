
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
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
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
import de.unkrig.commons.text.AbstractPrinter;
import de.unkrig.commons.text.LevelFilteredPrinter;
import de.unkrig.commons.text.Printers;
import de.unkrig.commons.text.RedirectablePrinter;
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
            @Override public void handle(String path, RuntimeException re) { Printers.error(path, re);  }
        });
    }
    @Nullable private Boolean    withPath; // null = "default behavior"
    private boolean              caseSensitive  = true;
    private final IncludeExclude includeExclude = new IncludeExclude();

    private final List<String> regexes = new ArrayList<String>();

    private final RedirectablePrinter  redirectablePrinter  = new RedirectablePrinter();
    private final LevelFilteredPrinter levelFilteredPrinter = new LevelFilteredPrinter(this.redirectablePrinter);

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
     * <p>
     *   By default, print each matching line, prefixed with the filename/path (iff two or more files are
     *   specified on the command line).
     * </p>
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

        final Main main = new Main();

        main.levelFilteredPrinter.run(new Runnable() { @Override public void run() { main.main2(args); } });
    }

    private void
    main2(String[] args) {

        try {
            this.main3(args);
        } catch (Exception e) {
            Printers.error(null, e);
            System.exit(2);
        }
    }

    private void
    main3(String[] args) throws IOException, InterruptedException {

        try {
            args = CommandLineOptions.parse(args, this);
        } catch (CommandLineOptionException cloe) {
            Printers.error(cloe.getMessage() + ", try \"--help\".");
            System.exit(2);
        }

        int argi = 0;

        // Next command line argument is the regex (unless "-e" was used).
        if (this.regexes.isEmpty()) {
            if (argi >= args.length) {
                System.err.println("Regex missing, try \"--help\".");
                System.exit(2);
            }
            this.regexes.add(args[argi++]);
        }

        // Configure search objects.
        for (String regex : this.regexes) {
            this.grep.addSearch(this.includeExclude, regex, this.caseSensitive);
        }

        // If neither "--with-path" nor "--no-path" was configured on the command line, compute the default behavior.
        this.grep.setWithPath(this.withPath != null ? this.withPath : args.length - argi >= 2);

        // Process files command line arguments.
        final List<File> files = new ArrayList<File>();
        while (argi < args.length) files.add(new File(args[argi++]));

        if (files.isEmpty()) {
            this.grep.contentsProcessor().process(
                "(standard input)",                                   // path
                System.in,                                            // inputStream
                null,                                                 // lastModifiedDate
                -1L,                                                  // size
                -1L,                                                  // crc32
                new ProducerWhichThrows<InputStream, IOException>() { // opener
                    @Override @Nullable public InputStream produce() { throw  new UnsupportedOperationException(); }
                }
            );
        } else {
            FileProcessings.process(files, this.grep.fileProcessor(true));
        }

        if (!this.grep.getLinesSelected()) System.exit(1);
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
     * Input contents encoding, default is "${file.encoding}".
     *
     * @main.commandLineOptionGroup Contents-Processing
     */
    @CommandLineOption public void
    setInputEncoding(Charset charset) { this.grep.setCharset(charset); }

    /**
     * Contents encoding, default is "${file.encoding}".
     *
     * @main.commandLineOptionGroup Contents-Processing
     */
    @CommandLineOption public void
    setOutputEncoding(Charset charset) {

        PrintWriter stdout = new PrintWriter(new OutputStreamWriter(System.out, charset), /*autoFlush*/ true);
        PrintWriter stderr = new PrintWriter(new OutputStreamWriter(System.err, charset), /*autoFlush*/ true);
        this.redirectablePrinter.setDelegate(new AbstractPrinter() {
            @Override public void error(@Nullable String message)   { stderr.println(message); }
            @Override public void warn(@Nullable String message)    { stderr.println(message); }
            @Override public void info(@Nullable String message)    { stdout.println(message); }
            @Override public void verbose(@Nullable String message) { stdout.println(message); }
            @Override public void debug(@Nullable String message)   { stdout.println(message); }
        });
    }

    /**
     * Input and output contents encoding, default is "${file.encoding}".
     *
     * @main.commandLineOptionGroup Contents-Processing
     */
    @CommandLineOption public void
    setEncoding(Charset charset) {
        this.setInputEncoding(charset);
        this.setOutputEncoding(charset);
    }

    /**
     * Print only filename/path, colon, and match count.
     *
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption(name = { "-c", "--count" }) public void
    setCount() { this.grep.setOperation(Operation.COUNT); }

    /**
     * Print only filename/path of documents containing matches.
     *
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption(name = { "-l", "--list", "--files-with-matches" }) public void
    setFilesWithMatches() { this.grep.setOperation(Operation.FILES_WITH_MATCHES); }

    /**
     * Print only filename/path of documents that do <em>not</em> contain any matches.
     *
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption(name = { "-L", "--files-without-match" }) public void
    setFilesWithoutMatch() { this.grep.setOperation(Operation.FILES_WITHOUT_MATCH); }

    /**
     * Print this instead of filename/path
     *
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption(name = "--label") public void
    setLabel(String value) { this.grep.setLabel(value); }

    /**
     * Prefix each match with the document filename/path (default if two or more files are specified on the command
     * line)
     *
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption(name = { "-H", "--with-path", "--with-filename" }) public void
    setWithPath() { this.withPath = true; }

    /**
     * Do <em>not</em> prefix each match with the document path (default if zero or one files are specified on the
     * command line)
     *
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption(name = { "-h", "--no-path", "--no-filename" }) public void
    setNoPath() { this.withPath = false; }

    /**
     * Prefix each match with the line number
     *
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption(name = { "-n", "--line-number" }) public void
    setLineNumber() { this.grep.setWithLineNumber(true); }

    /**
     * Prefix each match with the byte offset
     *
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption(name = { "-b", "--byte-offset" }) public void
    setByteOffset() { this.grep.setWithByteOffset(true); }

    /**
     * Print <var>n</var> lines of context after matching lines
     *
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption(name = { "-A", "--after-context" }) public void
    setAfterContext(int n) { this.grep.setAfterContext(n); }

    /**
     * Print <var>n</var> lines of context before matching lines
     *
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption(name = { "-B", "--before-context" }) public void
    setBeforeContext(int n) { this.grep.setBeforeContext(n); }

    /**
     * Print <var>n</var> lines of context before and after matching lines
     *
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption(name = { "-C", "--context" }) public void
    setContext(int n) {
        this.grep.setBeforeContext(n);
        this.grep.setAfterContext(n);
    }

    /**
     * Print only the matched parts; prints one line per match
     *
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption(name = { "-o", "--only-matching" }) public void
    setOnlyMatching() { this.grep.setOperation(Operation.ONLY_MATCHING); }

    /**
     * Stop reading after <var>n</var> matching lines
     *
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption(name = { "-m", "--max-count" }) public void
    setMaxCount(int n) { this.grep.setMaxCount(n); }

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
     * @main.commandLineOptionGroup Contents-Processing
     * @see Grep#setDisassembleClassFiles(boolean)
     */
    @CommandLineOption(name = { "da", "disassemble-class-files" }) public void
    setDisassemble() { this.grep.setDisassembleClassFiles(true); }

    /**
     * @main.commandLineOptionGroup Contents-Processing
     * @see Grep#setDisassembleClassFilesVerbose(boolean)
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
     * @main.commandLineOptionGroup Contents-Processing
     * @see Grep#setDisassembleClassFilesButHideLines(boolean)
     */
    @CommandLineOption(name = { "da-no-lines", "disassemble-class-files-but-hide-lines" }) public void
    setDisassembleClassFilesButHideLines() { this.grep.setDisassembleClassFilesButHideLines(true); }

    /**
     * @main.commandLineOptionGroup Contents-Processing
     * @see Grep#setDisassembleClassFilesButHideVars(boolean)
     */
    @CommandLineOption(name = { "da-no-vars", "disassemble-class-files-but-hide-vars" }) public void
    setDisassembleClassFilesButHideVars() { this.grep.setDisassembleClassFilesButHideVars(true); }

    /**
     * @main.commandLineOptionGroup Contents-Processing
     * @see Grep#setDisassembleClassFilesSymbolicLabels(boolean)
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
    @CommandLineOption(name = { "q", "quiet", "silent" }) public void
    setQuiet() {
        this.grep.setOperation(Operation.QUIET);

        this.levelFilteredPrinter.setQuiet();
        SimpleLogging.setQuiet();
    }

    /**
     * Suppress all messages except errors.
     *
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption public void
    setNowarn() {
        this.levelFilteredPrinter.setNoWarn();
        SimpleLogging.setNoWarn();
    }

    /**
     * Print the names of files and archive entries that are searched.
     *
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption public void
    setVerbose() {
        this.levelFilteredPrinter.setVerbose();
        SimpleLogging.setVerbose();
    }

    /**
     * Print verbose and debug messages.
     *
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption public void
    setDebug() {
        this.levelFilteredPrinter.setDebug();
        SimpleLogging.setDebug();
        SimpleLogging.setDebug();
        SimpleLogging.setDebug();
    }

    /**
     * Useful for <em>multiple</em> regexes or regexes starting with "-"
     *
     * @main.commandLineOptionGroup Contents-Processing
     */
    @CommandLineOption(
        name        = { "-e", "--regexp", "--regex" },
        cardinality = CommandLineOption.Cardinality.ANY
    ) public void
    addPattern(String regex) { this.regexes.add(regex); }

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
        System.exit(2);
    }
}
