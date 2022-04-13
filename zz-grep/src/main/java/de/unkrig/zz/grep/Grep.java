
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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import de.unkrig.commons.file.ExceptionHandler;
import de.unkrig.commons.file.contentsprocessing.ContentsProcessings;
import de.unkrig.commons.file.contentsprocessing.ContentsProcessor;
import de.unkrig.commons.file.contentsprocessing.SelectiveContentsProcessor;
import de.unkrig.commons.file.fileprocessing.FileProcessings;
import de.unkrig.commons.file.fileprocessing.FileProcessor;
import de.unkrig.commons.file.org.apache.commons.compress.archivers.ArchiveFormat;
import de.unkrig.commons.file.org.apache.commons.compress.archivers.ArchiveFormatFactory;
import de.unkrig.commons.file.org.apache.commons.compress.compressors.CompressionFormat;
import de.unkrig.commons.file.org.apache.commons.compress.compressors.CompressionFormatFactory;
import de.unkrig.commons.io.ByteFilterInputStream;
import de.unkrig.commons.io.InputStreams;
import de.unkrig.commons.io.IoUtil;
import de.unkrig.commons.io.OutputStreams;
import de.unkrig.commons.lang.protocol.ConsumerUtil;
import de.unkrig.commons.lang.protocol.ConsumerUtil.Produmer;
import de.unkrig.commons.lang.protocol.ConsumerWhichThrows;
import de.unkrig.commons.lang.protocol.NoException;
import de.unkrig.commons.lang.protocol.Predicate;
import de.unkrig.commons.lang.protocol.PredicateUtil;
import de.unkrig.commons.lang.protocol.Producer;
import de.unkrig.commons.lang.protocol.ProducerUtil;
import de.unkrig.commons.lang.protocol.ProducerWhichThrows;
import de.unkrig.commons.lang.protocol.RunnableWhichThrows;
import de.unkrig.commons.nullanalysis.Nullable;
import de.unkrig.commons.text.Printers;
import de.unkrig.commons.text.pattern.Finders.MatchResult2;
import de.unkrig.commons.text.pattern.Glob;
import de.unkrig.commons.text.pattern.PatternUtil;
import de.unkrig.commons.util.concurrent.ConcurrentUtil;
import de.unkrig.commons.util.concurrent.SquadExecutor;

/**
 * The central API for the ZZGREP functionality.
 */
public
class Grep {

    // BEGIN CONFIGURATION VARIABLES

    /**
     * Representation of the operation that should be executed by ZZGREP.
     */
    public
    enum Operation {

        /** For each match, print the file name, a colon, a space and the matched line. */
        NORMAL,

        /** Print only file name/path, colon, and match count. (Implements {@code "-c"}.) */
        COUNT,

        /** Print only the file name/path iff the document contains at least one match. (Implements {@code "-l"}.) */
        FILES_WITH_MATCHES,

        /** Print only the file name/path iff the documentdoes not contain any matches. (Implements {@code "-L"}.) */
        FILES_WITHOUT_MATCH,

        /** Print only the matched parts; one line per match. (Implements {@code "-o"}.) */
        ONLY_MATCHING,

        /** Do not print the matches. (Implements {@code "-q"}.) */
        QUIET,
    }

    private static final RuntimeException STOP_DOCUMENT = new RuntimeException();

    @Nullable private String              label;
    private boolean                       withPath;
    private boolean                       withLineNumber;
    private boolean                       withByteOffset;
    private int                           beforeContext, afterContext;
    private Predicate<? super String>     lookIntoFormat = PredicateUtil.always();
    private Charset                       charset        = Charset.defaultCharset();
    private Operation                     operation      = Operation.NORMAL;
    private int                           maxCount       = Integer.MAX_VALUE;
    private boolean                       inverted;
    private boolean                       disassembleClassFiles;
    private boolean                       disassembleClassFilesVerbose;
    @Nullable private File                disassembleClassFilesSourceDirectory;
    private boolean                       disassembleClassFilesButHideLines;
    private boolean                       disassembleClassFilesButHideVars;
    private boolean                       disassembleClassFilesSymbolicLabels;
    @Nullable private Comparator<Object>  directoryMemberNameComparator = Collator.getInstance();
    private ExceptionHandler<IOException> exceptionHandler              = ExceptionHandler.defaultHandler();

    private final
    class Search {
        final Glob    path;
        final Pattern pattern;

        /**
         * @param path    Which pathes this search applies to
         * @param pattern The regex to match the contents against
         */
        Search(Glob path, Pattern pattern) { this.path = path; this.pattern = pattern; }
    }

    private final List<Search> searches = new ArrayList<Search>();

    // END CONFIGURATION VARIABLES

    // BEGIN CONFIGURATION SETTERS

    /**
     * @param value Is printed instead of file/path names (Implements {@code "--label"}.)
     */
    public void
    setLabel(String value) { this.label = value; }

    /**
     * Whether to prefix each match with the document path and a colon.
     */
    public void
    setWithPath(boolean value) { this.withPath = value; }

    /**
     * Whether to prefix each match with the line number. (Implements {@code "-n"}.)
     */
    public void
    setWithLineNumber(boolean value) { this.withLineNumber = value; }

    /**
     * Whether to prefix each match with the byte offset. (Implements {@code "-b"}.)
     */
    public void
    setWithByteOffset(boolean value) { this.withByteOffset = value; }

    /**
     * Print <var>n</var> lines of context before matching lines. (Implements {@code "-B"}.)
     */
    public void
    setBeforeContext(int value) {
        if (value < 0) throw new IllegalArgumentException("Before-context-size must not be negative");
        this.beforeContext = value;
    }

    /**
     * Print <var>n</var> lines of context after matching lines. (Implements {@code "-A"}.)
     */
    public void
    setAfterContext(int value) {
        if (value < 0) throw new IllegalArgumentException("After-context-size must not be negative");
        this.afterContext = value;
    }

    /**
     * @param value Is evaluated against <code>"<i>format</i>:<i>path</i>"</code>
     * @see         ArchiveFormatFactory#allFormats()
     * @see         ArchiveFormat#getName()
     * @see         CompressionFormatFactory#allFormats()
     * @see         CompressionFormat#getName()
     */
    public void
    setLookIntoFormat(Predicate<? super String> value) { this.lookIntoFormat = value; }

    /** Sets a non-default charset for reading the files' contents. */
    public void
    setCharset(Charset value) { this.charset = value; }

    /**
     * The operation that should be executed by ZZGREP.
     *
     * @see Operation
     */
    public void
    setOperation(Operation value) { this.operation = value; }

    /**
     * Stop reading the current document after <var>n</var> matches. (Implements {@code "-m"}.)
     */
    public void
    setMaxCount(int n) { this.maxCount = n; }

    /**
     * Configures whether matching lines should be treated as non-matching, and vice versa. (Implements {@code "-v"}.)
     */
    public void
    setInverted(boolean value) { this.inverted = value; }

    /**
     * @param value Whether to disassemble Java&trade; class files on-the-fly before matching its contents
     */
    public void
    setDisassembleClassFiles(boolean value) { this.disassembleClassFiles = value; }

    /**
     * When disassembling .class files, include a constant pool dump, constant pool indexes, and hex dumps of all
     * attributes in the disassembly output.
     *
     * @param value Whether to enable or to disable the feature
     */
    public void
    setDisassembleClassFilesVerbose(boolean value) { this.disassembleClassFilesVerbose = value; }

    /**
     * @param value Where to look for source files; {@code null} disables source file loading; source file loading is
     *              disabled by default
     */
    public void
    setDisassembleClassFilesSourceDirectory(@Nullable File value) { this.disassembleClassFilesSourceDirectory = value; }

    /**
     * Don't print line numbers in the disassembly.
     *
     * @param value Whether to enable or to disable the feature
     */
    public void
    setDisassembleClassFilesButHideLines(boolean value) { this.disassembleClassFilesButHideLines = value; }

    /**
     * Don't print variable names in the disassembly.
     *
     * @param value Whether to enable or to disable the feature
     */
    public void
    setDisassembleClassFilesButHideVars(boolean value) { this.disassembleClassFilesButHideVars = value; }

    /**
     * When disassembling .class files, use symbolic labels (e.g. "L12") instead of numeric labels (like "#123").
     *
     * @param value Whether to enable or to disable the feature
     */
    public void
    setDisassembleClassFilesSymbolicLabels(boolean value) { this.disassembleClassFilesSymbolicLabels = value; }

    /**
     * @param path  Which pathes the search applies to
     * @param regex The regular expression to match each line against
     */
    public void
    addSearch(Glob path, String regex, boolean caseSensitive) {

        this.searches.add(
            new Search(path, Pattern.compile(
                regex,
                Pattern.MULTILINE | (caseSensitive ? 0 : Pattern.CASE_INSENSITIVE))
            )
        );
    }

    /**
     * Sets the exception handler.
     */
    public void
    setExceptionHandler(ExceptionHandler<IOException> exceptionHandler) { this.exceptionHandler = exceptionHandler; }

    /**
     * @param directoryMemberNameComparator The comparator used to sort a directory's members; a {@code null} value
     *                                      means to NOT sort the members, i.e. leave them in their 'natural' order as
     *                                      {@link File#list()} returns them
     */
    public void
    setDirectoryMemberNameComparator(@Nullable Comparator<Object> directoryMemberNameComparator) {
        this.directoryMemberNameComparator = directoryMemberNameComparator;
    }

    // END CONFIGURATION SETTERS

    // BEGIN SEARCH RESULT VARIABLES

    /**
     * The number of matches yielded by all invocations of {@link #grep()}.
     */
    int totalMatchCount;

    // END SEARCH RESULT VARIABLES

    // BEGIN SEARCH RESULT GETTERS

    /**
     * Whether any of processors yielded any matches.
     */
    public boolean
    getLinesSelected() { return this.totalMatchCount > 0; }

    /**
     * @return The number of matches yielded by all processors
     */
    public int
    getTotalMatchCount() { return this.totalMatchCount; }

    // END SEARCH RESULT GETTERS

    /**
     * @return A {@link FileProcessor} which executes the search and prints results to STDOUT
     */
    public FileProcessor<Void>
    fileProcessor(boolean lookIntoDirectories) {

        // Process only the files to which at least one search applies.
        Predicate<? super String> pathPredicate = PredicateUtil.never();
        for (Search search : this.searches) {
            pathPredicate = PredicateUtil.or(pathPredicate, search.path);
        }

        FileProcessor<Void> fp = FileProcessings.recursiveCompressedAndArchiveFileProcessor(
            this.lookIntoFormat,                            // lookIntoFormat
            PredicateUtil.always(),                         // pathPredicate
            ContentsProcessings.<Void>nopArchiveCombiner(), // archiveEntryCombiner
            new SelectiveContentsProcessor<Void>(           // contentsProcessor
                pathPredicate,                                   // pathPredicate
                this.contentsProcessor(),                        // trueDelegate
                ContentsProcessings.<Void>nopContentsProcessor() // falseDelegate
            ),
            this.exceptionHandler                           // exceptionHandler
        );

        // Honor the 'lookIntoDirectories' flag.
        if (lookIntoDirectories) {
            fp = FileProcessings.<Void>directoryTreeProcessor(
                pathPredicate,                                                       // pathPredicate
                fp,                                                                  // regularFileProcessor
                this.directoryMemberNameComparator,                                  // directoryMemberNameComparator
                FileProcessings.<Void>nopDirectoryCombiner(),                        // directoryCombiner
                new SquadExecutor<Void>(ConcurrentUtil.SEQUENTIAL_EXECUTOR_SERVICE), // squadExecutor
                this.exceptionHandler                                                // exceptionHandler
            );
        }

        return fp;
    }

    /**
     * @return A {@link ContentsProcessor} which executes the search and prints results to STDOUT
     */
    public ContentsProcessor<Void>
    contentsProcessor() {

        return new ContentsProcessor<Void>() {

            @Override @Nullable public Void
            process(
                String                                                            path,
                InputStream                                                       is,
                @Nullable Date                                                    lastModifiedDate,
                long                                                              size,
                long                                                              crc32,
                ProducerWhichThrows<? extends InputStream, ? extends IOException> opener
            ) throws IOException {

//                if (!Grep.this.includeExclude.matches(name)) return null;

                // Check which searches apply to this path.
                Pattern[] patterns;
                {
                    final List<Pattern> l = new ArrayList<Pattern>();
                    for (Search search : Grep.this.searches) {
                        if (search.path.matches(path)) l.add(search.pattern);
                    }
                    if (l.isEmpty()) return null;
                    patterns = l.toArray(new Pattern[l.size()]);
                }

                Printers.verbose(path);

                if (Grep.this.disassembleClassFiles && path.endsWith(".class")) {

                    // Wrap the input stream in a Java disassembler.
                    DisassemblerByteFilter disassemblerByteFilter = new DisassemblerByteFilter();

                    disassemblerByteFilter.setVerbose(Grep.this.disassembleClassFilesVerbose);
                    disassemblerByteFilter.setSourceDirectory(Grep.this.disassembleClassFilesSourceDirectory);
                    disassemblerByteFilter.setHideLines(Grep.this.disassembleClassFilesButHideLines);
                    disassemblerByteFilter.setHideVars(Grep.this.disassembleClassFilesButHideVars);
                    disassemblerByteFilter.setSymbolicLabels(Grep.this.disassembleClassFilesSymbolicLabels);
                    is = new ByteFilterInputStream(is, disassemblerByteFilter);
                }

                // For printing byte offsets ("-b").
                Producer<Long> bytesRead;
                if (Grep.this.withByteOffset) {
                    Produmer<Long, Number> p = ConsumerUtil.cumulate();
                    is = InputStreams.wye(is, OutputStreams.lengthWritten(p));
                    bytesRead = p;
                } else {
                    bytesRead = ProducerUtil.constantProducer(-1L);
                }

                // For printing path (-H) or label (--label).
                String label = Grep.this.label != null ? Grep.this.label : Grep.this.withPath ? path : null;

                int[]    matchCountInDocument = new int[1];
                Runnable incrementMatchCount = new Runnable() {

                    @Override public void
                    run() {
                        if (++matchCountInDocument[0] >= Grep.this.maxCount) throw Grep.STOP_DOCUMENT;
                    }
                };

                Reader r = new BufferedReader(new InputStreamReader(is, Grep.this.charset));

                Writer w;
                switch (Grep.this.operation) {

                case NORMAL:
                    w = Grep.grepNormal(
                        patterns,
                        Grep.this.inverted,
                        label,
                        Grep.this.withLineNumber,
                        Grep.this.beforeContext,
                        Grep.this.afterContext,
                        bytesRead,
                        incrementMatchCount
                    );
                    break;

                case ONLY_MATCHING:
                    // In this mode, we may or may not track line numbers.
                    if (Grep.this.withLineNumber) {
                        w = Grep.grepPrintMatchesWithLineNumber(
                            patterns,
                            Grep.this.inverted,
                            label,
                            bytesRead,
                            incrementMatchCount
                        );
                    } else {
                        w = Grep.grepPrintMatches(
                            patterns,
                            true, // printMatches
                            label,
                            bytesRead,
                            incrementMatchCount
                        );
                    }
                    break;

                case COUNT:
                    // In this mode, we don't have to track line numbers.
                    w = Grep.grepPrintMatches(
                        patterns,
                        false, // printMatches
                        label,
                        bytesRead,
                        incrementMatchCount
                    );
                    break;

                case FILES_WITH_MATCHES:
                case FILES_WITHOUT_MATCH:
                case QUIET:
                    // In these modes, we don't have to track line numbers, and we only check if there *is* a match.
                    w = Grep.grepCheck(patterns, incrementMatchCount);
                    break;

                default:
                    throw new AssertionError(Grep.this.operation);
                }

                // Now process the contents.
                try {
                    IoUtil.copy(r, w);
                    w.close();
                } catch (RuntimeException re) {
                    if (re != Grep.STOP_DOCUMENT) throw re;
                }

                Grep.this.totalMatchCount += matchCountInDocument[0];

                // Print the per-document epilog.
                switch (Grep.this.operation) {

                case NORMAL:
                case QUIET:
                case ONLY_MATCHING:
                    break;

                case COUNT:
                    Printers.info((Grep.this.label != null ? Grep.this.label : path) + ':' + matchCountInDocument[0]);
                    break;

                case FILES_WITH_MATCHES:
                    if (matchCountInDocument[0] > 0) Printers.info(Grep.this.label != null ? Grep.this.label : path);
                    break;

                case FILES_WITHOUT_MATCH:
                    if (matchCountInDocument[0] == 0) Printers.info(Grep.this.label != null ? Grep.this.label : path);
                    break;

                default:
                    throw new AssertionError(Grep.this.operation);
                }

                return null;
            }
        };
    }

    /**
     * Creates and returns a writer which implements a "normal" grep, optionally with label, line number,
     * before-context, and after-context.
     *
     * @param label         Printed before each match, if non-null
     * @param beforeContext Number of lines to print before each match
     * @param afterContext  Number of lines to print after each match
     * @param countMatch    Is called after each match
     */
    private static Writer
    grepNormal(
        Pattern[]                              patterns,
        boolean                                inverted,
        @Nullable String                       label,
        boolean                                withLineNumber,
        int                                    beforeContext,
        int                                    afterContext,
        ProducerWhichThrows<Long, NoException> bytesRead,
        Runnable                               countMatch
    ) {
        StringBuilder currentLine           = new StringBuilder();
        boolean[]     currentLineHasMatches = new boolean[1];

        ConsumerWhichThrows<MatchResult2, ? extends IOException>
        match = new ConsumerWhichThrows<MatchResult2, IOException>() {

            @Override public void
            consume(MatchResult2 mr2) {

                // Don't print the match *now*, only when the line is completely read.
                currentLineHasMatches[0] = true;
                currentLine.append(mr2.group());
            }
        };

        ConsumerWhichThrows<Character, ? extends IOException>
        nonMatch = ConsumerUtil.<IOException>lineCounter(
            new ConsumerWhichThrows<Character, IOException>() { // lineChar
                @Override public void consume(Character c) { currentLine.append(c); }
            },
            new ConsumerWhichThrows<Integer, IOException>() {   // lineComplete

                LinkedList<String> beforeContextLines = new LinkedList<String>();
                int                afterContextLinesToPrint;

                @Override public void
                consume(Integer lineNumber) {

                    if (currentLineHasMatches[0] ^ inverted) {
                        this.matchingLine(currentLine.toString(), lineNumber, bytesRead.produce());
                        countMatch.run();
                    } else {
                        this.nonMatchingLine(currentLine.toString(), lineNumber, bytesRead.produce());
                    }

                    currentLine.setLength(0);
                    currentLineHasMatches[0] = false;
                }

                boolean hadMatch;

                private void
                matchingLine(String line, int lineNumber, long byteOffset) {

                    // Iff a "context" is configured (and be it zero!), print a separator line between chunks:
                    if (
                        (beforeContext > 0 || afterContext > 0)
                        && this.beforeContextLines.size() == beforeContext
                        && this.afterContextLinesToPrint == 0
                        && this.hadMatch
                    ) Printers.info("--");
                    this.hadMatch = true;

                    // Print the "before context lines".
                    while (!this.beforeContextLines.isEmpty()) Printers.info(this.beforeContextLines.remove());

                    // Print the matching line.
                    Printers.info(Grep.composeMatch(label, withLineNumber ? lineNumber : -1, byteOffset, line, ':'));

                    // Remember how many "after context" lines are to be printed.
                    this.afterContextLinesToPrint = afterContext;
                }

                private void
                nonMatchingLine(String line, int lineNumber, long byteOffset) {

                    // Are there any context lines to print after a preceeding match?
                    if (this.afterContextLinesToPrint > 0) {
                        Printers.info(Grep.composeMatch(label, withLineNumber ? lineNumber : -1, byteOffset, line, '-'));
                        this.afterContextLinesToPrint--;
                    } else
                    if (beforeContext > 0) {

                        // Keep a copy of the line in case a future match would like to print "before context".
                        if (this.beforeContextLines.size() >= beforeContext) this.beforeContextLines.remove();
                        this.beforeContextLines.add(Grep.composeMatch(label, withLineNumber ? lineNumber : -1, byteOffset, line, '-'));
                    }
                }
            }
        );

        RunnableWhichThrows<? extends IOException>
        flush = new RunnableWhichThrows<IOException>() {

            @Override public void
            run() throws IOException {
                if (currentLine.length() > 0) nonMatch.consume('\n');
            }
        };

        return PatternUtil.patternFinderWriter(patterns, match, nonMatch, flush);
    }

    /**
     * Creates and returns a writer which info-prints matches of the <var>patterns</var> within the character
     * stream, together with <var>path</var>, line number, and <var>bytesRead</var>.
     *
     * @param countMatch Is called after each match
     */
    private static Writer
    grepPrintMatchesWithLineNumber(
        Pattern[]                              patterns,
        boolean                                inverted,
        @Nullable String                       label,
        ProducerWhichThrows<Long, NoException> bytesRead,
        Runnable                               countMatch
    ) {
        int[]     lineNumber            = new int[1];
        boolean[] currentLineHasMatches = new boolean[1];

        ConsumerWhichThrows<MatchResult2, ? extends IOException>
        match = new ConsumerWhichThrows<MatchResult2, IOException>() {

            @Override public void
            consume(MatchResult2 mr2) {
                currentLineHasMatches[0] = true;
                Printers.info(Grep.composeMatch(label, lineNumber[0] + 1, bytesRead.produce(), mr2.group(), ':'));
            }
        };

        final ConsumerWhichThrows<Character, ? extends IOException>
        nonMatch = ConsumerUtil.<IOException>lineCounter(
            ConsumerUtil.<Character, IOException>widen2(ConsumerUtil.nop()), // lineChar
            new ConsumerWhichThrows<Integer, IOException>() {                // lineComplete

                @Override public void
                consume(Integer lineNumber2) {
                    lineNumber[0] = lineNumber2;
                    if (currentLineHasMatches[0] ^ inverted) {
                        countMatch.run();
                    }
                    currentLineHasMatches[0] = false;
                }
            }
        );

        RunnableWhichThrows<? extends IOException>
        flush = new RunnableWhichThrows<IOException>() {
            @Override public void run() throws IOException { nonMatch.consume('\n'); }
        };

        return PatternUtil.patternFinderWriter(patterns, match, nonMatch, flush);
    }

    /**
     * Creates and returns a writer which counts matches of the <var>patterns</var> within the character
     * stream.
     * <p>
     *   If <var>printMatches</var>, then each match is info-printed together with <var>path</var> and
     *   <var>bytesRead</var>.
     * </p>
     *
     * @param printMatches Whether also to info-print the matches
     * @param countMatch   Is called after each match
     */
    private static Writer
    grepPrintMatches(
        Pattern[]                              patterns,
        boolean                                printMatches,
        @Nullable String                       label,
        ProducerWhichThrows<Long, NoException> bytesRead,
        Runnable                               countMatch
    ) {

        ConsumerWhichThrows<MatchResult2, ? extends IOException>
        match = new ConsumerWhichThrows<MatchResult2, IOException>() {
            @Override public void consume(MatchResult2 mr2) {
                if (printMatches) {
                    Printers.info(Grep.composeMatch(
                        label,               // label
                        -1,                  // lineNumber
                        bytesRead.produce(), // byteOffset
                        mr2.group(),         // text
                        ':'                  // separator
                    ));
                }
                countMatch.run();
            }
        };

        ConsumerWhichThrows<Character, ? extends IOException>
        nonMatch = ConsumerUtil.<Character, IOException>widen2(ConsumerUtil.nop());

        return PatternUtil.patternFinderWriter(patterns, match, nonMatch);
    }

    /**
     * Creates and returns a writer which, on the first match of any of the <var>patterns</var> within the character
     * stream, runs <var>countMatch</var> and throws {@link #STOP_DOCUMENT}.
     */
    private static Writer
    grepCheck(Pattern[] patterns, Runnable countMatch) {

        ConsumerWhichThrows<MatchResult2, ? extends IOException>
        match = new ConsumerWhichThrows<MatchResult2, IOException>() {
            @Override public void consume(MatchResult2 mr2) {
                countMatch.run();
                throw Grep.STOP_DOCUMENT;
            }
        };

        ConsumerWhichThrows<Character, ? extends IOException>
        nonMatch = ConsumerUtil.<Character, IOException>widen2(ConsumerUtil.nop());

        return PatternUtil.patternFinderWriter(patterns, match, nonMatch);
    }

    private static String
    composeMatch(@Nullable String label, int lineNumber, long byteOffset, String text, char separator) {

        if (label == null && lineNumber < 0 && byteOffset < 0) return text;

        StringBuilder sb = new StringBuilder();
        if (label != null) sb.append(label).append(separator);
        if (lineNumber >= 0) sb.append(lineNumber).append(separator);
        if (byteOffset >= 0) sb.append(byteOffset).append(separator);
        sb.append(text);

        return sb.toString();
    }
}
