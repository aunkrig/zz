
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
import de.unkrig.commons.lang.protocol.Predicate;
import de.unkrig.commons.lang.protocol.PredicateUtil;
import de.unkrig.commons.lang.protocol.ProducerWhichThrows;
import de.unkrig.commons.lang.protocol.RunnableUtil;
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
    private int                           beforeContext = -1, afterContext = -1;
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
     * Print <var>n</var> lines of context after matching lines. (Implements {@code "-A"}.)
     */
    public void
    setAfterContext(int value) { this.afterContext = value; }

    /**
     * Print <var>n</var> lines of context before matching lines. (Implements {@code "-B"}.)
     */
    public void
    setBeforeContext(int value) { this.beforeContext = value; }

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
                final List<Pattern> patterns = new ArrayList<Pattern>();
                for (Search search : Grep.this.searches) {
                    if (search.path.matches(path)) patterns.add(search.pattern);
                }
                if (patterns.isEmpty()) return null;

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
                Produmer<Long, Number> bytesRead = ConsumerUtil.cumulate();
                if (Grep.this.withByteOffset) {
                    is = InputStreams.wye(is, OutputStreams.lengthWritten(bytesRead));
                }

                int[] matchCountInDocument = new int[1];
                int[] lineNumber           = { 1 };

                Reader r = new BufferedReader(new InputStreamReader(is, Grep.this.charset));

                // These will be called which the contents is processed.
                ConsumerWhichThrows<MatchResult2, ? extends IOException> match;
                ConsumerWhichThrows<Character, ? extends IOException>    nonMatch;
                RunnableWhichThrows<? extends IOException>               flush;

                switch (Grep.this.operation) {

                case NORMAL:
                    StringBuilder      currentLine              = new StringBuilder();
                    boolean[]          currentLineHasMatches    = new boolean[1];
                    boolean[]          crPending                = new boolean[1];
                    LinkedList<String> beforeContext            = new LinkedList<String>();
                    int[]              afterContextLinesToPrint = new int[1];

                    match = new ConsumerWhichThrows<MatchResult2, IOException>() {

                        @Override public void
                        consume(MatchResult2 mr2) {
                            currentLineHasMatches[0] = true;
                            crPending[0] = false;
                            currentLine.append(mr2.group());
                        }
                    };

                    nonMatch = new ConsumerWhichThrows<Character, IOException>() {

                        @Override public void
                        consume(Character c) {

                            if (crPending[0] && c == '\n') return;

                            if (c == '\r') {
                                crPending[0] = true;
                            } else
                            if (c == '\n') {
                                ;
                            } else
                            {
                                currentLine.append(c);
                                return;
                            }

                            if (currentLineHasMatches[0] ^ Grep.this.inverted) {
                                this.matchingLine(currentLine.toString(), bytesRead.produce());
                                matchCountInDocument[0]++;
                                Grep.this.totalMatchCount++;
                                if (matchCountInDocument[0] >= Grep.this.maxCount) throw Grep.STOP_DOCUMENT;
                            } else {
                                this.nonMatchingLine(currentLine.toString(), bytesRead.produce());
                            }
                            lineNumber[0]++;
                            currentLine.setLength(0);
                            currentLineHasMatches[0] = false;
                        }

                        private void
                        matchingLine(String line, long byteOffset) {

                            // Iff a "context" is configured (and be it zero!), print a separator line between chunks:
                            if (
                                (Grep.this.beforeContext != -1 || Grep.this.afterContext != -1)
                                && beforeContext.size() == (Grep.this.beforeContext == -1 ? 0 : Grep.this.beforeContext)
                                && afterContextLinesToPrint[0] == 0
                                && Grep.this.totalMatchCount > 0
                            ) Printers.info("--");

                            // Print the "before context lines".
                            while (!beforeContext.isEmpty()) Printers.info(beforeContext.remove());

                            // Print the matching line.
                            Printers.info(composeMatch(path, lineNumber[0], byteOffset, line, ':'));

                            // Remember how many "after context" lines are to be printed.
                            afterContextLinesToPrint[0] = Grep.this.afterContext;
                        }

                        private void
                        nonMatchingLine(String line, long byteOffset) {

                            // Are there any context lines to print after a preceeding match?
                            if (afterContextLinesToPrint[0] > 0) {
                                Printers.info(composeMatch(path, lineNumber[0], byteOffset, line, '-'));
                                afterContextLinesToPrint[0]--;
                            } else
                            if (Grep.this.beforeContext > 0) {

                                // Keep a copy of the line in case a future match would like to print "before context".
                                if (beforeContext.size() >= Grep.this.beforeContext) beforeContext.remove();
                                beforeContext.add(composeMatch(path, lineNumber[0], byteOffset, line, '-'));
                            }
                        }
                    };

                    flush = new RunnableWhichThrows<IOException>() {

                        @Override public void
                        run() throws IOException {
                            if (currentLine.length() > 0) nonMatch.consume('\n');
                        }
                    };
                    break;

                // In these modes, we don't have to track line numbers.
                case COUNT:
                case ONLY_MATCHING:
                    match = new ConsumerWhichThrows<MatchResult2, IOException>() {
                        @Override public void consume(MatchResult2 mr2) {
                            matchCountInDocument[0]++;
                            Grep.this.totalMatchCount++;
                            if (Grep.this.operation == Operation.ONLY_MATCHING) {
                                Printers.info(composeMatch(path, lineNumber[0], bytesRead.produce(), mr2.group(), ':'));
                            }
                            if (matchCountInDocument[0] >= Grep.this.maxCount) throw Grep.STOP_DOCUMENT;
                        }
                    };
                    nonMatch = ConsumerUtil.<Character, IOException>widen2(ConsumerUtil.nop());
                    flush    = RunnableUtil.asRunnableWhichThrows(RunnableUtil.NOP);
                    break;

                // In these modes, we don't have to track line numbers, and we only check if there *is* a match.
                case FILES_WITH_MATCHES:
                case FILES_WITHOUT_MATCH:
                case QUIET:
                    match = new ConsumerWhichThrows<MatchResult2, IOException>() {
                        @Override public void consume(MatchResult2 mr2) {
                            matchCountInDocument[0]++;
                            Grep.this.totalMatchCount++;
                            throw Grep.STOP_DOCUMENT;
                        }
                    };
                    nonMatch = ConsumerUtil.<Character, IOException>widen2(ConsumerUtil.nop());
                    flush    = RunnableUtil.asRunnableWhichThrows(RunnableUtil.NOP);
                    break;

                default:
                    throw new AssertionError();
                }

                Writer w = PatternUtil.patternFinderWriter(patterns.toArray(new Pattern[patterns.size()]), match, nonMatch);

                try {
                    IoUtil.copy(r, w);
                    flush.run();
                } catch (RuntimeException re) {
                    if (re != Grep.STOP_DOCUMENT) throw re;
                }

                // Per document:
                switch (Grep.this.operation) {

                case NORMAL:
                case QUIET:
                case ONLY_MATCHING:
                    break;

                case COUNT:
                    Printers.info(path + ':' + matchCountInDocument[0]);
                    break;

                case FILES_WITH_MATCHES:
                    if (matchCountInDocument[0] > 0) Printers.info(path);
                    break;

                case FILES_WITHOUT_MATCH:
                    if (matchCountInDocument[0] == 0) Printers.info(path);
                    break;
                }

                return null;
            }

            private String
            composeMatch(String path, int lineNumber, long byteOffset, String text, char separator) {

                StringBuilder sb = new StringBuilder();
                if (Grep.this.withPath) {
                    sb.append(Grep.this.label != null ? Grep.this.label : path).append(separator);
                }

                if (Grep.this.withLineNumber) sb.append(lineNumber).append(separator);

                if (Grep.this.withByteOffset) sb.append(byteOffset).append(separator);

                sb.append(text);

                return sb.toString();
            }
        };
    }

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
}
