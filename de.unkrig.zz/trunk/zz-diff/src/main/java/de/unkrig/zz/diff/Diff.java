
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
 *    3. The name of the author may not be used to endorse or promote products derived from this software without
 *       specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package de.unkrig.zz.diff;

import static de.unkrig.commons.text.scanner.JavaScanner.TokenType.CXX_COMMENT;
import static de.unkrig.commons.text.scanner.JavaScanner.TokenType.C_COMMENT;
import static de.unkrig.commons.text.scanner.JavaScanner.TokenType.MULTI_LINE_C_COMMENT_BEGINNING;
import static de.unkrig.commons.text.scanner.JavaScanner.TokenType.MULTI_LINE_C_COMMENT_END;
import static de.unkrig.commons.text.scanner.JavaScanner.TokenType.MULTI_LINE_C_COMMENT_MIDDLE;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

import org.incava.util.diff.Difference;

import de.unkrig.commons.file.ExceptionHandler;
import de.unkrig.commons.file.contentsprocessing.ContentsProcessings;
import de.unkrig.commons.file.contentsprocessing.ContentsProcessings.ArchiveCombiner;
import de.unkrig.commons.file.contentsprocessing.ContentsProcessor;
import de.unkrig.commons.file.fileprocessing.FileProcessings;
import de.unkrig.commons.file.fileprocessing.FileProcessings.DirectoryCombiner;
import de.unkrig.commons.file.fileprocessing.FileProcessor;
import de.unkrig.commons.file.fileprocessing.SelectiveFileProcessor;
import de.unkrig.commons.io.ByteFilterInputStream;
import de.unkrig.commons.lang.AssertionUtil;
import de.unkrig.commons.lang.ExceptionUtil;
import de.unkrig.commons.lang.ThreadUtil;
import de.unkrig.commons.lang.protocol.Predicate;
import de.unkrig.commons.lang.protocol.PredicateUtil;
import de.unkrig.commons.lang.protocol.ProducerWhichThrows;
import de.unkrig.commons.nullanalysis.Nullable;
import de.unkrig.commons.text.Printer;
import de.unkrig.commons.text.Printers;
import de.unkrig.commons.text.scanner.AbstractScanner.Token;
import de.unkrig.commons.text.scanner.JavaScanner;
import de.unkrig.commons.text.scanner.JavaScanner.TokenType;
import de.unkrig.commons.text.scanner.ScanException;
import de.unkrig.commons.text.scanner.ScannerUtil;
import de.unkrig.commons.text.scanner.StringScanner;
import de.unkrig.commons.util.TreeComparator;
import de.unkrig.commons.util.TreeComparator.Node;
import de.unkrig.commons.util.concurrent.ConcurrentUtil;
import de.unkrig.commons.util.concurrent.SquadExecutor;

/**
 * Implementation of the ZZ DIFF utility.
 *
 * <p>
 *   It prints its output via the {@link Printers context printer}; if you want to modify the printing, then you'll
 *   have to set up your own {@link Printer} and use {@link Printers#withPrinter(Printer, Runnable)} to execute the
 *   DIFF.
 * </p>
 */
public
class Diff {

    static { AssertionUtil.enableAssertionsForThisClass(); }

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private static final ExecutorService PARALLEL_EXECUTOR_SERVICE = new ScheduledThreadPoolExecutor(
        Runtime.getRuntime().availableProcessors() * 3,
        ThreadUtil.DAEMON_THREAD_FACTORY
    );

    /**
     * Iff the paths of the two contents sources match the {@link #pathPattern}, and the line from source 1 ("line 1")
     * and the line from source 2 ("Line 2") both match the {@link #lineRegex}, and the capturing groups have
     * equal text, then the two lines are regarded as "equal", although their texts may not be equal.
     */
    public static
    class LineEquivalence {

        /**
         * To which files / elements this object applies.
         */
        public final Predicate<? super String> pathPattern;

        /**
         * The regex that is applied to each line.
         */
        public final Pattern lineRegex;

        public
        LineEquivalence(Predicate<? super String> pathPattern, Pattern lineRegex) {
            this.pathPattern = pathPattern;
            this.lineRegex   = lineRegex;
        }

        @Override public String
        toString() {
            return this.pathPattern + ":" + this.lineRegex;
        }
    }

    /**
     * The DIFF output format.
     */
    public
    enum DiffMode {

        /**
         * Report only which files were added or deleted; content changes are <em>not</em> reported.
         */
        EXIST,

        /**
         * Report only which files were added, deleted or changed.
         */
        BRIEF,

        /**
         * Output 'normal' DIFF output.
         */
        NORMAL,

        /**
         * Output 'context diff' format.
         */
        CONTEXT,

        /**
         * Output 'unified diff' format.
         */
        UNIFIED
    }

    /**
     * How added and deleted files are handled.
     */
    public
    enum AbsentFileMode {

        /**
         * Report about added resp. deleted files and directories.
         */
        REPORT,

        /**
         * Compare added resp. deleted file with the empty document; compare added resp. deleted directories with the
         * empty directory.
         */
        COMPARE_WITH_EMPTY,

        /**
         * Ignore added resp. deleted files and directories.
         */
        IGNORE,
    }

    // Configuration parameters.

    /** The possible modes for tokenizing the documents to compare. */
    public enum Tokenization { LINE, JAVA }

    private Predicate<? super String>         pathPredicate   = PredicateUtil.always();
    private final List<Pattern>               equivalentPaths = new ArrayList<Pattern>();
    private final Collection<LineEquivalence> equivalentLines = new ArrayList<LineEquivalence>();
    private final Collection<LineEquivalence> ignores         = new ArrayList<LineEquivalence>();
    private boolean                           ignoreWhitespace;
    private AbsentFileMode                    addedFileMode   = AbsentFileMode.REPORT;
    private AbsentFileMode                    deletedFileMode = AbsentFileMode.REPORT;
    private boolean                           reportUnchangedFiles;
    private Predicate<? super String>         lookIntoFormat  = PredicateUtil.always();
    private boolean                           disassembleClassFiles;
    private boolean                           disassembleClassFilesButHideLines;
    private boolean                           disassembleClassFilesButHideVars;
    private Charset                           charset          = Charset.defaultCharset();
    private DiffMode                          diffMode         = DiffMode.NORMAL;
    private int                               contextSize      = 3;
    private ExceptionHandler<IOException>     exceptionHandler = ExceptionHandler.<IOException>defaultHandler();
    private boolean                           sequential;
    private Tokenization                      tokenization     = Tokenization.LINE;
    private boolean                           ignoreCStyleComments;
    private boolean                           ignoreCPlusPlusStyleComments;
    private boolean                           ignoreDocComments;

    // SETTERS FOR THE VARIOUS CONFIGURATION PARAMETERS

    // CHECKSTYLE JavadocMethod:OFF
    public void
    setIgnoreWhitespace(boolean value) { this.ignoreWhitespace = value; }

    public void
    setAddedFileMode(AbsentFileMode value) { this.addedFileMode = value; }

    public void
    setDeletedFileMode(AbsentFileMode value) { this.deletedFileMode = value; }

    public void
    setReportUnchangedFiles(boolean value) { this.reportUnchangedFiles = value; }

    public void
    setLookInto(Predicate<? super String> value) { this.lookIntoFormat = value; }

    public void
    setDisassembleClassFiles(boolean value) { this.disassembleClassFiles = value; }

    public void
    setDisassembleClassFilesButHideLines(boolean value) { this.disassembleClassFilesButHideLines = value; }

    public void
    setDisassembleClassFilesButHideVars(boolean value) { this.disassembleClassFilesButHideVars = value; }

    public void
    setCharset(Charset value) { this.charset = value; }

    public void
    setDiffMode(DiffMode value) { this.diffMode = value; }

    public void
    setContextSize(int value) { this.contextSize = value; }

    public void
    setExceptionHandler(ExceptionHandler<IOException> value) { this.exceptionHandler = value; }

    public void
    setSequential(boolean value) { this.sequential = value; }

    public void
    setTokenization(Tokenization value) { this.tokenization = value; }

    /**
     * Whether C-style comments ("<code>/*; ... &#42;/</code>") are relevant for comparison.
     * Relevant iff {@link #setTokenization(Tokenization) tokenization} is {@link Tokenization#JAVA JAVA}.
     * <p>
     *   Doc comments ("<code>/** ... &#42;/</code>") are handled differently, and are not regarded as C-style
     *   comments.
     * </p>
     * <p>
     *   The default is {@code false}.
     * </p>
     *
     * @see #setIgnoreDocComments(boolean)
     */
    public void
    setIgnoreCStyleComments(boolean value) { this.ignoreCStyleComments = value; }

    /**
     * Whether C++-style comments ("<code>// ...</code>") are relevant for comparison.
     * Relevant iff {@link #setTokenization(Tokenization) tokenization} is {@link Tokenization#JAVA JAVA}.
     * <p>
     *   The default is {@code false}.
     * </p>
     */
    public void
    setIgnoreCPlusPlusStyleComments(boolean value) { this.ignoreCPlusPlusStyleComments = value; }

    /**
     * Whether doc comments ("<code>/** ... &#42;/</code>") are relevant for comparison.
     * Relevant iff {@link #setTokenization(Tokenization) tokenization} is {@link Tokenization#JAVA JAVA}.
     * <p>
     *   The default is {@code false}.
     * </p>
     * <p>
     *   Strictly speaking, a doc comment is only a doc comment if it appears <i>immediately before a declaration</i>;
     *   however, this implementation regards <i>any</i> comment starting with "{@code /**}" as a doc comment.
     * </p>
     */
    public void
    setIgnoreDocComments(boolean value) { this.ignoreDocComments = value; }

    public void
    setPathPredicate(Predicate<? super String> pathPredicate) { this.pathPredicate = pathPredicate; }

    public void
    addEquivalentPath(Pattern path) { this.equivalentPaths.add(path); }

    public void
    addEquivalentLine(LineEquivalence lineEquivalence) { this.equivalentLines.add(lineEquivalence); }

    public void
    addIgnore(LineEquivalence lineEquivalence) { this.ignores.add(lineEquivalence); }
    // CHECKSTYLE MethodCheck:ON

    /**
     * Creates two trees of directories, normal files, compressed files, archive files and archive entries, compares
     * them and reports all differences.
     */
    public long
    execute(File file1, File file2) throws IOException, InterruptedException {

        SquadExecutor<NodeWithPath> squadExecutor = new SquadExecutor<NodeWithPath>(
            this.sequential ? ConcurrentUtil.SEQUENTIAL_EXECUTOR_SERVICE : Diff.PARALLEL_EXECUTOR_SERVICE
        );

        FileProcessor<NodeWithPath> dfp = this.fileProcessor(squadExecutor);

        Printers.verbose("Scanning ''{0}''...", file1);
        final NodeWithPath node1 = dfp.process("", file1);
        if (node1 == null) {
            Printers.error("\"" + file1 + "\" does not exist or is excluded");
            return 0;
        }

        Printers.verbose("Scanning ''{0}''...", file2);
        final NodeWithPath node2 = dfp.process("", file2);
        if (node2 == null) {
            Printers.error("\"" + file2 + "\" does not exist or is excluded");
            return 0;
        }

        try {
            squadExecutor.awaitCompletion();
        } catch (ExecutionException ee) {
            try { throw ee.getCause(); }
            catch (IOException      ioe) { throw ioe; }
            catch (RuntimeException re)  { throw re; }
            catch (Error            e)   { throw e; } // SUPPRESS CHECKSTYLE IllegalCatch
            catch (Throwable        t)   { throw new AssertionError(t); }
        }

        Printers.verbose("Computing differences...");

        long differenceCount = this.diff(file1.getPath(), file2.getPath(), node1, node2);

        Printers.verbose("{0,choice,0#No differences|1#1 difference|1<{0} differences} found.", differenceCount);

        return differenceCount;
    }

    /**
     * Creates two trees of directories, normal files, compressed files, archive files and archive entries, compares
     * them and reports all differences.
     */
    public long
    execute(
        ProducerWhichThrows<? extends InputStream, IOException> opener1,
        ProducerWhichThrows<? extends InputStream, IOException> opener2
    ) throws IOException {

        Printers.verbose("Scanning first stream...");
        final NodeWithPath node1 = this.scanStream(opener1);

        Printers.verbose("Scanning second stream...");
        final NodeWithPath node2 = this.scanStream(opener2);

        Printers.verbose("Computing differences...");

        long differenceCount = this.diff("(first)", "(second)", node1, node2);

        Printers.verbose("{0,choice,0#No differences|1#1 difference|1<{0} differences} found.", differenceCount);

        return differenceCount;
    }

    private NodeWithPath
    scanStream(ProducerWhichThrows<? extends InputStream, IOException> opener) throws IOException {

        ContentsProcessor<NodeWithPath> dcp = this.contentsProcessor();

        InputStream stream1 = AssertionUtil.notNull(opener.produce());

        final NodeWithPath node1;
        try {
            node1 = dcp.process("", stream1, -1L, -1L, opener);
            stream1.close();
        } finally {
            try { stream1.close(); } catch (Exception e) {}
        }
        assert node1 != null;
        return node1;
    }

    /**
     * Notice that the returned {@link FileProcessor} may return {@code null} if, e.g., the file is excluded, or
     * is a directory which can impossibly contain relevant (not-excluded) documents.
     */
    private FileProcessor<NodeWithPath>
    fileProcessor(SquadExecutor<NodeWithPath> squadExecutor) {

        ArchiveCombiner<NodeWithPath> archiveEntryCombiner = new ArchiveCombiner<Diff.NodeWithPath>() {

            @Override @Nullable public NodeWithPath
            combine(String archivePath, List<NodeWithPath> archiveCombinables) {
                TreeSet<NodeWithPath> archiveNodes = new TreeSet<NodeWithPath>(Diff.this.normalizedPathComparator);
                archiveNodes.addAll(archiveCombinables);
                return new ArchiveNode(archivePath, archiveNodes);
            }
        };

        FileProcessor<NodeWithPath>
        regularFileProcessor = FileProcessings.recursiveCompressedAndArchiveFileProcessor(
            this.lookIntoFormat,      // lookIntoFormat
            archiveEntryCombiner,     // archiveEntryCombiner
            this.contentsProcessor(), // contentsProcessor
            this.exceptionHandler     // exceptionHandler
        );
        regularFileProcessor = new SelectiveFileProcessor<NodeWithPath>(
            this.pathPredicate,
            regularFileProcessor,
            FileProcessings.<NodeWithPath>nop()
        );

        return FileProcessings.directoryTreeProcessor(
            this.pathPredicate,                     // pathPredicate
            regularFileProcessor,                   // regularFileProcessor
            Collator.getInstance(),                 // directoryMemberNameComparator
            new DirectoryCombiner<NodeWithPath>() { // directoryMemberCombiner

                @Override public NodeWithPath
                combine(String directoryPath, File directory, List<NodeWithPath> memberCombinables) {

                    // Filter out "null" values, which can
                    TreeSet<NodeWithPath> memberNodes = new TreeSet<NodeWithPath>(Diff.this.normalizedPathComparator);
                    for (NodeWithPath memberNode : memberCombinables) {
                        if (memberNode != null) memberNodes.add(memberNode);
                    }
                    return new DirectoryNode(directoryPath, memberNodes);
                }
            },
            squadExecutor,                          // squadExecutor
            this.exceptionHandler                   // exceptionHandler
        );
    }

    /**
     * @return The root of the hierarchy read from the <var>inputStream</var>; is never {@code null}
     */
    private ContentsProcessor<NodeWithPath>
    contentsProcessor() {

        ContentsProcessor<NodeWithPath> normalContentsProcessor = new ContentsProcessor<Diff.NodeWithPath>() {

            @Override @Nullable public NodeWithPath
            process(
                final String                                                            path,
                InputStream                                                             inputStream,
                long                                                                    size,
                long                                                                    crc32,
                final ProducerWhichThrows<? extends InputStream, ? extends IOException> opener
            ) throws IOException {

                // Compute size and crc32 eagerly because the inputStream is cheaper than the opener
                final long finalSize;
                final int  finalCrc;
                if (size != -1 && crc32 != -1) {
                    finalSize = size;
                    finalCrc  = (int) crc32;
                } else {
                    SizeAndCrc32 sizeAndCrc32 = Diff.sizeAndCrc32(inputStream);
                    finalSize = sizeAndCrc32.getSize();
                    finalCrc  = sizeAndCrc32.getCrc32();
                }

                return new DocumentNode(path) {

                    @Override public InputStream
                    open() throws IOException { return AssertionUtil.notNull(opener.produce()); }

                    @Override public long   getSize()  { return finalSize; }
                    @Override public int    getCrc32() { return finalCrc; }
                    @Override public String toString() { return path; }
                };
            }
        };
        return ContentsProcessings.recursiveCompressedAndArchiveContentsProcessor(
            this.lookIntoFormat,
            new ArchiveCombiner<NodeWithPath>() {

                @Override @Nullable public NodeWithPath
                combine(String archivePath, List<NodeWithPath> combinables) {
                    SortedSet<NodeWithPath>
                    archiveEntries = new TreeSet<Diff.NodeWithPath>(Diff.this.normalizedPathComparator);
                    archiveEntries.addAll(combinables);
                    return new ArchiveNode(archivePath, archiveEntries);
                }
            },
            normalContentsProcessor,
            this.exceptionHandler
        );
    }

    /**
     * Converts a file/element path into a 'normalized' String which honors the 'equivalent paths' patterns.
     */
    private String
    normalize(String path) {
        for (Pattern pathEquivalence : Diff.this.equivalentPaths) {
            Matcher matcher = pathEquivalence.matcher(path.substring(1));
            if (matcher.matches()) {
                path = path.substring(0, 1);
                for (int i = 1; i <= matcher.groupCount(); i++) {
                    path += matcher.group(i);
                }
            }
        }
        return path;
    }

    /**
     * Print the differences between the two entry sets to STDOUT.
     *
     * @return The number of differences
     */
    private long
    diff(final String path1, final String path2, NodeWithPath node1, final NodeWithPath node2) throws IOException {

        final long[] differenceCount = new long[1];

        TreeComparator<NodeWithPath, IOException> treeComparator = new TreeComparator<NodeWithPath, IOException>() {

            @Override protected void
            nodeAdded(NodeWithPath node) throws IOException {

                switch (Diff.this.addedFileMode) {

                case REPORT:
                    Diff.this.reportFileAdded(node.getPath());
                    break;

                case COMPARE_WITH_EMPTY:
                    for (DocumentNode document : this.getDocuments(node)) {
                        String path = document.getPath();
                        differenceCount[0] += Diff.this.diff("(missing)", path, null, Diff.this.readAllLines(
                            document.open(),
                            path2 + path
                        ));
                    }
                    break;

                case IGNORE:
                    ;
                    break;

                default:
                    throw new AssertionError();
                }
            }

            @Override protected void
            nodeDeleted(NodeWithPath node) throws IOException {

                switch (Diff.this.deletedFileMode) {

                case REPORT:
                    Diff.this.reportFileDeleted(node.getPath());
                    break;

                case COMPARE_WITH_EMPTY:
                    for (DocumentNode document : this.getDocuments(node)) {
                        String path = document.getPath();
                        differenceCount[0] += Diff.this.diff(path, "(missing)", Diff.this.readAllLines(
                            document.open(),
                            path1 + path
                        ), null);
                    }
                    break;

                case IGNORE:
                    ;
                    break;

                default:
                    throw new AssertionError();
                }
            }

            private List<DocumentNode>
            getDocuments(NodeWithPath node) {

                SortedSet<NodeWithPath> children = node.children();
                if (children == null) return Collections.singletonList((DocumentNode) node);

                final List<DocumentNode> result = new ArrayList<DocumentNode>();
                for (NodeWithPath child : children) {
                    result.addAll(this.getDocuments(child));
                }
                return result;
            }

            @Override protected void
            nonLeafNodeChangedToLeafNode(NodeWithPath node1, NodeWithPath node2) {
                Diff.this.reportFileChanged(node1.getPath(), node2.getPath());
            }

            @Override protected void
            leafNodeChangedToNonLeafNode(NodeWithPath node1, NodeWithPath node2) {
                Diff.this.reportFileChanged(node1.getPath(), node2.getPath());
            }

            @Override protected void
            leafNodeRemains(NodeWithPath node1, NodeWithPath node2) throws IOException {
                DocumentNode document1 = (DocumentNode) node1;
                DocumentNode document2 = (DocumentNode) node2;

                String path1 = document1.getPath();
                String path2 = document2.getPath();

                // Are the files' contents bytewise identical?
                // Notice: For some DocumentNodes the computation of the CRC32 is quite expensive, so check size
                //         equality before crc32 equality.
                if (
                    document1.getSize() == document2.getSize()
                    && document1.getCrc32() == document2.getCrc32()
                ) {
                    Diff.this.reportFileUnchanged(path1, path2);
                    return;
                }

                if (Diff.this.diffMode == DiffMode.EXIST) {
                    Diff.this.reportFileUnchanged(path1, path2);
                } else {

                    // Read the contents of the two pathes.
                    Line[] lines1 = Diff.this.readAllLines(document1.open(), path1);
                    Line[] lines2 = Diff.this.readAllLines(document2.open(), path2);

                    // The two files have PHYSICALLY different contents, but they may still be LOGICALLY equal.
                    differenceCount[0] += Diff.this.diff(path1, path2, lines1, lines2);
                }
            }
        };

        treeComparator.compare(node1, node2);

        return differenceCount[0];
    }

    /** Report that a document was added. */
    protected void
    reportFileAdded(String path) {
        Printers.verbose("''{0}'' added", path);

        switch (Diff.this.diffMode) {

        case EXIST:
        case BRIEF:
            Printers.info(path.length() == 0 ? "File added" : "+ " + path.substring(1));
            break;

        case NORMAL:
        case CONTEXT:
        case UNIFIED:
            if (path.length() > 0) Printers.info("File added " + path.substring(1));
            break;
        }
    }

    /** Report that a document was deleted. */
    protected void
    reportFileDeleted(String path) {
        Printers.verbose("''{0}'' deleted", path);

        switch (Diff.this.diffMode) {

        case EXIST:
        case BRIEF:
            Printers.info(path.length() == 0 ? "File deleted" : "- " + path.substring(1));
            break;

        case NORMAL:
        case CONTEXT:
        case UNIFIED:
            if (path.length() > 0) Printers.info("File deleted " + path.substring(1));
            break;
        }
    }

    /** Report that the contents of a document changed. */
    protected void
    reportFileChanged(String path1, String path2) {
        Printers.verbose("''{0}'' and ''{1}'' changed", path1, path2);

        switch (Diff.this.diffMode) {

        case EXIST:
        case BRIEF:
            Printers.info(path2.length() == 0 ? "File changed" : "! " + path2.substring(1));
            break;

        case NORMAL:
        case CONTEXT:
        case UNIFIED:
            if (path1.length() > 0) Printers.info("File changed " + path2.substring(1));
            break;
        }
    }

    /** Report that the contents of a file is unchanged. */
    protected void
    reportFileUnchanged(String path1, String path2) {
        Printers.verbose("''{0}'' and ''{1}'' unchanged", path1, path2);

        if (Diff.this.reportUnchangedFiles) {
            switch (Diff.this.diffMode) {

            case EXIST:
            case BRIEF:
                Printers.info(path1.length() == 0 ? "File unchanged" : "= " + path2.substring(1));
                break;

            case NORMAL:
            case CONTEXT:
            case UNIFIED:
                if (path1.length() > 0) Printers.info("File unchanged " + path2.substring(1));
                break;
            }
        }
    }

    /**
     * Analyzes the lines of the two documents and reports that the contents is "equal" or has "changed", and,
     * in the latter case, prints the actual differences and increments the {@link #differenceCount}.
     *
     * @param lines1 {@code null} means {@code path1} designates a 'deleted' file
     * @param lines2 {@code null} means {@code path2} designates an 'added' file
     */
    private long
    diff(String path1, String path2, @Nullable Line[] lines1, @Nullable Line[] lines2) {
        Printers.verbose(
            "''{0}'' ({1} lines) vs. ''{2}'' ({3} lines)",
            path1,
            lines1 == null ? "no" : lines1.length,
            path2,
            lines2 == null ? "no" : lines2.length
        );

        // Compute the contents differences.
        List<Difference> diffs = this.logicalDiff(
            lines1 == null ? new Line[0] : lines1,
            lines2 == null ? new Line[0] : lines2
        );
        Printers.verbose("{0} raw {0,choice,0#differences|1#difference|differences} found", diffs.size());

        if (diffs.isEmpty()) {
            Diff.this.reportFileUnchanged(path1, path2);
            return 0;
        }

        // A difference is ignored iff all added and deleted lines match any of the 'ignore' patterns.
        final List<Pattern> fileIgnores = new ArrayList<Pattern>();
        for (LineEquivalence ignore : Diff.this.ignores) {
            if (ignore.pathPattern.evaluate(path1)) {
                fileIgnores.add(ignore.lineRegex);
            }
        }
        if (!fileIgnores.isEmpty()) {

            IGNORABLE:
            for (Iterator<Difference> it = diffs.iterator(); it.hasNext();) {
                Difference d = it.next();

                if (lines1 != null && d.getDeletedStart() != Difference.NONE) {
                    for (int i = d.getDeletedStart(); i <= d.getDeletedEnd(); i++) {
                        if (!Diff.contains(lines1[i].text, fileIgnores)) continue IGNORABLE;
                    }
                }
                if (lines2 != null && d.getAddedStart() != Difference.NONE) {
                    for (int i = d.getAddedStart(); i <= d.getAddedEnd(); i++) {
                        if (!Diff.contains(lines2[i].text, fileIgnores)) continue IGNORABLE;
                    }
                }
                it.remove();
            }
            Printers.verbose("Reduced to {0} non-ignorable differences", diffs.size());
        }

        if (lines1 == null) {
            Diff.this.reportFileAdded(path2);
        } else
        if (lines2 == null) {
            Diff.this.reportFileDeleted(path1);
        } else
        if (diffs.isEmpty()) {
            Diff.this.reportFileUnchanged(path1, path2);
            return 0;
        } else
        {
            Diff.this.reportFileChanged(path1, path2);
        }

        // Report the actual differences.
        switch (Diff.this.diffMode) {

        case EXIST:
            // We should not come here because EXIST mode is checked way before.
            throw new AssertionError();

        case BRIEF:
            break;

        case NORMAL:
            Diff.normalDiff(
                lines1 == null ? new Line[0] : lines1,
                lines2 == null ? new Line[0] : lines2,
                diffs
            );
            break;

        case CONTEXT:
            Printers.info("*** " + path1);
            Printers.info("--- " + path2);
            Diff.this.contextDiff(
                lines1 == null ? new Line[0] : lines1,
                lines2 == null ? new Line[0] : lines2,
                diffs
            );
            break;

        case UNIFIED:
            Printers.info("--- " + path1);
            Printers.info("+++ " + path2);
            Diff.this.unifiedDiff(
                lines1 == null ? new Line[0] : lines1,
                lines2 == null ? new Line[0] : lines2,
                diffs
            );
            break;

        default:
            throw new AssertionError();
        }

        return diffs.size();
    }

    private List<Difference>
    logicalDiff(Line[] lines1, Line[] lines2) {

        switch (this.tokenization) {

        case LINE:
            return new org.incava.util.diff.Diff<Line>(lines1, lines2).diff();

        case JAVA:
            Map<Integer, Integer> tokenIndexToLineIndex1 = new HashMap<Integer, Integer>(lines1.length);
            Map<Integer, Integer> tokenIndexToLineIndex2 = new HashMap<Integer, Integer>(lines2.length);

            List<String> tokens1 = this.tokenize(lines1, tokenIndexToLineIndex1);
            List<String> tokens2 = this.tokenize(lines2, tokenIndexToLineIndex2);

            // Transform the list of "token differences" into a list of "line differences". Since there can be more
            // than one "token diff" per line, the resulting list could be shorter than the original list.
            List<Difference> diffs = new org.incava.util.diff.Diff<Object>(tokens1.toArray(), tokens2.toArray()).diff();
            List<Difference> tmp   = new ArrayList<Difference>();
            if (!diffs.isEmpty()) {

                Difference d = diffs.get(0);

                int ds1 = d.getDeletedStart();
                if (ds1 != -1) ds1 = tokenIndexToLineIndex1.get(ds1);
                int de1 = d.getDeletedEnd();
                if (de1 != -1) de1 = tokenIndexToLineIndex1.get(de1);
                int as1 = d.getAddedStart();
                if (as1 != -1) as1 = tokenIndexToLineIndex2.get(as1);
                int ae1 = d.getAddedEnd();
                if (ae1 != -1) ae1 = tokenIndexToLineIndex2.get(ae1);

                for (int i = 1; i < diffs.size(); i++) {

                    d = diffs.get(i);

                    int ds2 = d.getDeletedStart();
                    if (ds2 != -1) ds2 = tokenIndexToLineIndex1.get(ds2);
                    int de2 = d.getDeletedEnd();
                    if (de2 != -1) de2 = tokenIndexToLineIndex1.get(de2);
                    int as2 = d.getAddedStart();
                    if (as2 != -1) as2 = tokenIndexToLineIndex2.get(as2);
                    int ae2 = d.getAddedEnd();
                    if (ae2 != -1) ae2 = tokenIndexToLineIndex2.get(ae2);

                    if (
                        (de1 == -1 ? ds2 <= ds1 + 1 : ds2 == de1)
                        || (ae1 == -1 ? as2 <= as1 + 1 : as2 == ae1)
                    ) {

                        // Change 2 is in the same line as change 1; merge the two.
                        de1 = de2 != -1 ? de2 : ds2;
                        ae1 = ae2 != -1 ? ae2 : as2;
                    } else {

                        // Change 2 is not in the same line as change 1.
                        tmp.add(new Difference(ds1, de1, as1, ae1));
                        ds1 = ds2;
                        de1 = de2;
                        as1 = as2;
                        ae1 = ae2;
                    }
                }

                // Last difference.
                tmp.add(new Difference(ds1, de1, as1, ae1));
            }

            return tmp;

        default:
            throw new AssertionError(this.tokenization);
        }
    }

    /**
     * Scans the given <var>lines</var> into Java tokens and returns them. While doing that, the mapping from token
     * index (0, 1, 2, ...) to line index (0, 1, 2, ...) is stored in <var>tokenIndexToLineIndex</var>.
     * <p>
     *   The whitespace between Java tokens is always ignored;
     *   C-style comments ("<code>/&#42; ... &#42;/</code>") are ignored depending on the {@link
     *   #setIgnoreCStyleComments(boolean)} configuration parameter;
     *   C++-style comments ("<code>// ...</code>") are ignored depending on the {@link
     *   #setIgnoreCPlusPlusStyleComments(boolean)} configuration parameter.
     * </p>
     */
    private List<String>
    tokenize(Line[] lines, Map<Integer, Integer> tokenIndexToLineIndex) {

        List<String> tokens = new ArrayList<String>(lines.length);

        // Set up a Java scanner that swallows the tokens that should be ignored (space, C-style comments, C++-style
        // comments), depending on the configuration.
        StringScanner<TokenType>
        ss = ScannerUtil.filter(JavaScanner.rawStringScanner(), new Predicate<Token<TokenType>>() {

            boolean ignoreThisComment;

            @Override public boolean
            evaluate(@Nullable Token<TokenType> token) {

                // "null" means "end of input", and that must not be ignored.
                if (token == null) return true;

                // Identify a C-style or a doc comment.
                if (
                    token.type == C_COMMENT
                    || token.type == MULTI_LINE_C_COMMENT_BEGINNING
                ) {
                    this.ignoreThisComment = (
                        token.text.startsWith("/**")
                        ? Diff.this.ignoreDocComments
                        : Diff.this.ignoreCStyleComments
                    );
                }
                if (
                    token.type == C_COMMENT
                    || token.type == MULTI_LINE_C_COMMENT_BEGINNING
                    || token.type == MULTI_LINE_C_COMMENT_MIDDLE
                    || token.type == MULTI_LINE_C_COMMENT_END
                ) {
                    return !this.ignoreThisComment;
                }

                // Identify a C++-style comment.
                if (token.type == CXX_COMMENT) return !Diff.this.ignoreCPlusPlusStyleComments;

                // Only SPACE and "real" tokens should be left at this point.
                return token.type != TokenType.SPACE;
            }
        });

        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex].toString();
            ss.setInput(line);
            try {
                for (Token<TokenType> t = ss.produce(); t != null; t = ss.produce()) {
                    tokenIndexToLineIndex.put(tokens.size(), lineIndex);
                    tokens.add(t.text);
                }
            } catch (ScanException se) {

                // Ignore any scanning problems.
                ;
            }
        }

        return tokens;
    }

    /** @return Whether the {@code text} contains any of the {@code patterns} */
    private static boolean
    contains(String text, Iterable<Pattern> patterns) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(text).find()) return true;
        }
        return false;
    }

    /**
     * Format a list of {@link Difference}s in "normal diff style".
     */
    private static void
    normalDiff(Line[] lines1, Line[] lines2, List<Difference> diffs) {
        for (Difference diff : diffs) {
            int delStart = diff.getDeletedStart();
            int delEnd   = diff.getDeletedEnd();
            int addStart = diff.getAddedStart();
            int addEnd   = diff.getAddedEnd();

            Printers.info(
                Diff.toString(delStart, delEnd)
                + (delEnd == Difference.NONE ? "a" : addEnd == Difference.NONE ? "d" : "c")
                + Diff.toString(addStart, addEnd)
            );

            if (delEnd != Difference.NONE) {
                Diff.printLines(delStart, delEnd, "< ", lines1);
                if (addEnd != Difference.NONE) Printers.info("---");
            }
            if (addEnd != Difference.NONE) {
                Diff.printLines(addStart, addEnd, "> ", lines2);
            }
        }
    }

    /**
     * Format a list of {@link Difference}s in "context diff" style.
     */
    private void
    contextDiff(final Line[] lines1, final Line[] lines2, List<Difference> diffs) {
        this.chunkedDiff(diffs, new ChunkPrinter() {

            @Override public void
            print(List<Difference> chunk) {
                Printers.info("***************");

                Difference firstDifference = chunk.get(0);
                Difference lastDifference  = chunk.get(chunk.size() - 1);

                // Print file one aggregated differences.
                {
                    int boc1 = Math.max(0, firstDifference.getDeletedStart() - Diff.this.contextSize);
                    int eoc1 = Math.min((
                        lastDifference.getDeletedEnd() == Difference.NONE
                        ? lastDifference.getDeletedStart() + Diff.this.contextSize - 1
                        : lastDifference.getDeletedEnd() + Diff.this.contextSize
                    ), lines1.length - 1);
                    Printers.info("*** " + Diff.toString(boc1, eoc1) + " ****");
                    for (Difference d : chunk) {
                        Diff.printLines(boc1, d.getDeletedStart() - 1, "  ", lines1);
                        if (d.getDeletedEnd() == Difference.NONE) {
                            boc1 = d.getDeletedStart();
                        } else {
                            Diff.printLines(
                                d.getDeletedStart(),
                                d.getDeletedEnd(),
                                d.getAddedEnd() == Difference.NONE ? "- " : "! ",
                                lines1
                            );
                            boc1 = d.getDeletedEnd() + 1;
                        }
                    }
                    Diff.printLines(boc1, eoc1, "  ", lines1);
                }

                // Print file two aggregated differences.
                {
                    int boc2 = Math.max(0, firstDifference.getAddedStart() - Diff.this.contextSize);
                    int eoc2 = Math.min((
                        lastDifference.getAddedEnd() == Difference.NONE
                        ? lastDifference.getAddedStart() + Diff.this.contextSize - 1
                        : lastDifference.getAddedEnd() + Diff.this.contextSize
                    ), lines2.length - 1);
                    Printers.info("--- " + Diff.toString(boc2, eoc2) + " ----");
                    for (Difference d : chunk) {
                        Diff.printLines(boc2, d.getAddedStart() - 1, "  ", lines2);
                        if (d.getAddedEnd() == Difference.NONE) {
                            boc2 = d.getAddedStart();
                        } else {
                            Diff.printLines(
                                d.getAddedStart(),
                                d.getAddedEnd(),
                                d.getDeletedEnd() == Difference.NONE ? "+ " : "! ",
                                lines2
                            );
                            boc2 = d.getAddedEnd() + 1;
                        }
                    }
                    Diff.printLines(boc2, eoc2, "  ", lines2);
                }
            }
        });
    }

    /**
     * Format a list of {@link Difference}s in "context diff" style.
     */
    private void
    unifiedDiff(final Line[] lines1, final Line[] lines2, List<Difference> diffs) {
        this.chunkedDiff(diffs, new ChunkPrinter() {

            @Override public void
            print(List<Difference> chunk) {

                Difference firstDifference = chunk.get(0);
                Difference lastDifference  = chunk.get(chunk.size() - 1);

                int boc1 = Math.max(0, firstDifference.getDeletedStart() - Diff.this.contextSize);
                int eoc1 = Math.min((
                    lastDifference.getDeletedEnd() == Difference.NONE
                    ? lastDifference.getDeletedStart() + Diff.this.contextSize - 1
                    : lastDifference.getDeletedEnd() + Diff.this.contextSize
                ), lines1.length - 1);
                int boc2 = Math.max(0, firstDifference.getAddedStart() - Diff.this.contextSize);
                int eoc2 = Math.min((
                    lastDifference.getAddedEnd() == Difference.NONE
                    ? lastDifference.getAddedStart() + Diff.this.contextSize - 1
                    : lastDifference.getAddedEnd() + Diff.this.contextSize
                ), lines2.length - 1);
                Printers.info(
                    "@@ -"
                    + (boc1 + 1)
                    +  ","
                    +  (eoc1 - boc1 + 1)
                    +  " +"
                    +  (boc2 + 1)
                    +  ","
                    +  (eoc2 - boc2 + 1)
                    +  " @@"
                );

                for (Difference d : chunk) {
                    Diff.printLines(boc1, d.getDeletedStart() - 1, " ", lines1);
                    if (d.getDeletedEnd() == Difference.NONE) {
                        boc1 = d.getDeletedStart();
                    } else {
                        Diff.printLines(d.getDeletedStart(), d.getDeletedEnd(), "-", lines1);
                        boc1 = d.getDeletedEnd() + 1;
                    }
                    if (d.getAddedEnd() == Difference.NONE) {
                        boc2 = d.getAddedStart();
                    } else {
                        Diff.printLines(d.getAddedStart(), d.getAddedEnd(), "+", lines2);
                        boc2 = d.getAddedEnd() + 1;
                    }
                }
                Diff.printLines(boc1, eoc1, " ", lines1);
            }
        });
    }

    private void
    chunkedDiff(List<Difference> diffs, ChunkPrinter chunkPrinter) {
        Iterator<Difference> it        = diffs.iterator();
        Difference           lookahead = it.hasNext() ? it.next() : null;
        while (lookahead != null) {

            // Aggregate differences with 2*CONTEXT_SIZE or less lines in between.
            List<Difference> agg = new ArrayList<Difference>();
            agg.add(lookahead);
            for (;;) {
                final int afterDeletion = (
                    lookahead.getDeletedEnd() == Difference.NONE
                    ? lookahead.getDeletedStart()
                    : lookahead.getDeletedEnd() + 1
                );
                lookahead = it.hasNext() ? it.next() : null;
                if (lookahead == null) break;
                if (lookahead.getDeletedStart() - afterDeletion <= 2 * Diff.this.contextSize) {
                    agg.add(lookahead);
                } else {
                    break;
                }
            }

            // Now print one aggregation.
            chunkPrinter.print(agg);
        }
    }

    /**
     * @see #print(List)
     */
    private
    interface ChunkPrinter {

        /**
         * Formats a list of {@link Difference}s and prints them to {@link Diff#out}.
         */
        void print(List<Difference> chunk);
    }

    private static String
    toString(int start, int end) {
        StringBuilder sb = new StringBuilder();

        sb.append(end == Difference.NONE ? start : start + 1);

        if (end != Difference.NONE && start != end) {
            sb.append(",").append(end + 1);
        }
        return sb.toString();
    }

    private static void
    printLines(int start, int end, String indicator, Line[] lines) {
        for (int lnum = start; lnum <= end; ++lnum) {
            Printers.info(indicator + lines[lnum]);
        }
    }

    /**
     * Reads the contents of the entry with the given {@code path} and transforms it to an array of {@link Line}s.
     * Honors {@link #disassembleClassFiles}, {@link #equivalentLines} and {@link #ignoreWhitespace}.
     *
     * @param path E.g. ".class" files are filtered through a bytecode disassembler
     */
    private Line[]
    readAllLines(InputStream inputStream, String path) throws IOException {
        try {
            return this.readAllLines2(inputStream, path);
        } finally {
            try { inputStream.close(); } catch (IOException ioe) {}
        }
    }

    /**
     * Reads the contents of the given {@link InputStream} and transforms it to an array of {@link Line}s. The
     * {@link InputStream} is not closed. Honors {@link #disassembleClassFiles}, {@link #equivalentLines} and {@link
     * #ignoreWhitespace}.
     *
     * @param path E.g. ".class" files are filtered through a bytecode disassembler
     */
    private Line[]
    readAllLines2(InputStream is, String path) throws IOException {

        // Deploy the .class file disassembler as appropriate.
        if (this.disassembleClassFiles && path.endsWith(".class")) {
            DisassemblerByteFilter disassemblerByteFilter = new DisassemblerByteFilter();
            disassemblerByteFilter.setHideLines(this.disassembleClassFilesButHideLines);
            disassemblerByteFilter.setHideVars(this.disassembleClassFilesButHideVars);
            is = new ByteFilterInputStream(is, disassemblerByteFilter);
        }

        BufferedReader br = new BufferedReader(new InputStreamReader(is, this.charset));

        Collection<Pattern> equivalences = new ArrayList<Pattern>();
        for (LineEquivalence le : this.equivalentLines) {
            if (le.pathPattern.evaluate(path)) {
                equivalences.add(le.lineRegex);
            }
        }

        List<Line> contents = new ArrayList<Line>();
        try {
            for (;;) {
                Line line = this.readLine(br, equivalences);
                if (line == null) break;
                contents.add(line);
            }
        } catch (IOException ioe) {
            throw ExceptionUtil.wrap("Reading '" + path + "'", ioe);
        } catch (RuntimeException re) {
            throw ExceptionUtil.wrap("Reading '" + path + "'", re);
        }

        return contents.toArray(new Line[contents.size()]);
    }

    /**
     * @return {@code null} on end-of-input
     */
    @Nullable private Line
    readLine(BufferedReader br, Collection<Pattern> equivalences) throws IOException {
        final String line = br.readLine();
        if (line == null) return null;

        return new Line(line, equivalences);
    }

    private
    interface Checksummable {

        /**
         * Updates the given {@link Checksum} from this object.
         */
        void update(Checksum checksum);
    }

    /**
     * Representation of a line read from a stream. Honors {@link Diff#ignoreRegexes} and {@link
     * Diff#ignoreWhitespace}.
     */
    private
    class Line implements Checksummable {

        private final String  text;
        private byte[]        value;

        Line(String text, Collection<Pattern> equivalences) {
            try {
                this.text = text;

                if (Diff.this.ignoreWhitespace) {
                    text = Diff.WHITESPACE_PATTERN.matcher(text).replaceAll(" ");
                }

                for (Pattern p : equivalences) {
                    Matcher matcher = p.matcher(text);
                    if (matcher.find()) {
                        if (matcher.groupCount() == 0) {
                            this.value = Diff.IGNORED_LINE;
                            return;
                        }

                        StringBuffer sb = new StringBuffer();
                        do {
                            String replacement = "";
                            for (int i = 1; i <= matcher.groupCount(); i++) {
                                replacement += "$" + i;
                            }
                            matcher.appendReplacement(sb, replacement);
                        } while (matcher.find());
                        matcher.appendTail(sb);
                        text = sb.toString();
                    }
                }
                this.value = text.getBytes("UTF-8");
            } catch (UnsupportedEncodingException uee) {
                throw new RuntimeException(uee);
            }
        }

        @Override public boolean
        equals(@Nullable Object o) {
            if (!(o instanceof Line)) return false;
            Line that = (Line) o;
            return this == that || Arrays.equals(this.value, that.value);
        }

        @Override public int
        hashCode() { return Arrays.hashCode(this.value); }

        /**
         * Returns the line's text.
         */
        @Override public String
        toString() { return this.text; }

        /**
         * Updates the given {@link Checksum} from this object's value.
         */
        @Override public void
        update(Checksum checksum) {
            checksum.update(this.value, 0, this.value.length);
        }
    }
    private static final byte[] IGNORED_LINE = { 0x7f, 0x2f, 0x19 };

    private final Comparator<? super NodeWithPath> normalizedPathComparator = new Comparator<NodeWithPath>() {

        @Override public int
        compare(@Nullable NodeWithPath node1, @Nullable NodeWithPath node2) {
            assert node1 != null;
            assert node2 != null;
            return Diff.this.normalize(node1.getPath()).compareTo(Diff.this.normalize(node2.getPath()));
        }
    };

    private abstract
    class NodeWithPath implements Node<NodeWithPath> {

        private final String path;

        NodeWithPath(String path) { this.path = path; }

        /**
         * @return The "path" of the node, as specified through {@link #Diff(String, File)}
         */
        public String
        getPath() { return this.path; }

        @Override public abstract String
        toString();
    }

    private
    class DirectoryNode extends NodeWithPath {

        private final SortedSet<NodeWithPath> children;

        DirectoryNode(String path, SortedSet<NodeWithPath> children) {
            super(path);
            this.children = children;
        }

        @Override public SortedSet<NodeWithPath>
        children() { return this.children; }

        @Override public String
        toString() { return "dir:" + this.getPath(); }
    }

    private
    class ArchiveNode extends NodeWithPath {

        private final SortedSet<NodeWithPath> archiveNodes;

        ArchiveNode(String path, SortedSet<NodeWithPath> archiveNodes) {
            super(path);
            this.archiveNodes = archiveNodes;
        }

        @Override @Nullable public SortedSet<NodeWithPath>
        children() { return this.archiveNodes; }

        @Override public String
        toString() { return "archive:" + this.getPath(); }
    }

    private abstract
    class DocumentNode extends NodeWithPath {

        DocumentNode(String path) { super(path); }

        @Override @Nullable public SortedSet<NodeWithPath> children() { return null; }

        public abstract long
        getSize();

        public abstract int
        getCrc32() throws IOException;

        /** @return The contents of this node */
        public abstract InputStream
        open() throws IOException;

        @Override public String
        toString() { return "doc:" + this.getPath(); }
    }

    /** Container for a 'size' and a 'crc32'. */
    public
    interface SizeAndCrc32 {

        /** @return The size, which is always &gt;= 0 */
        long getSize();

        /** @return The crc32 */
        int  getCrc32();
    }

    /**
     * @return The byte count and the CRC32 of the contents of the given {@code inputStream}
     */
    public static SizeAndCrc32
    sizeAndCrc32(InputStream inputStream) throws IOException {

        long  size  = 0;
        CRC32 crc32 = new CRC32();

        byte[] buffer = new byte[8192];
        for (;;) {
            int count = inputStream.read(buffer);
            if (count == -1) break;
            crc32.update(buffer, 0, count);
            size += count;
        }

        final long finalSize  = size;
        final int  finalCrc32 = (int) crc32.getValue();
        return new SizeAndCrc32() {
            @Override public long getSize()  { return finalSize; }
            @Override public int  getCrc32() { return finalCrc32; }
        };
    }
}
