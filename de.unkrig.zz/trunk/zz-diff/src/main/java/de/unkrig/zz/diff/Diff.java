
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
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

import de.unkrig.commons.file.ExceptionHandler;
import de.unkrig.commons.file.contentsprocessing.ContentsProcessings;
import de.unkrig.commons.file.contentsprocessing.ContentsProcessings.ArchiveCombiner;
import de.unkrig.commons.file.contentsprocessing.ContentsProcessor;
import de.unkrig.commons.file.fileprocessing.FileProcessor;
import de.unkrig.commons.file.resourceprocessing.ResourceProcessings;
import de.unkrig.commons.file.resourceprocessing.ResourceProcessor;
import de.unkrig.commons.io.InputStreams;
import de.unkrig.commons.lang.AssertionUtil;
import de.unkrig.commons.lang.ThreadUtil;
import de.unkrig.commons.lang.protocol.Consumer;
import de.unkrig.commons.lang.protocol.Predicate;
import de.unkrig.commons.lang.protocol.PredicateUtil;
import de.unkrig.commons.lang.protocol.ProducerWhichThrows;
import de.unkrig.commons.lang.protocol.RunnableWhichThrows;
import de.unkrig.commons.nullanalysis.Nullable;
import de.unkrig.commons.text.AbstractPrinter;
import de.unkrig.commons.text.AbstractPrinter.Level;
import de.unkrig.commons.text.Printer;
import de.unkrig.commons.text.Printers;
import de.unkrig.commons.util.TreeComparator;
import de.unkrig.commons.util.TreeComparator.Node;
import de.unkrig.commons.util.concurrent.ConcurrentUtil;
import de.unkrig.commons.util.concurrent.SquadExecutor;

/**
 * Implementation of the ZZ DIFF utility.
 *
 * <p>
 *   It prints its output via the {@link Printers context printer}; if you want to modify the printing, then you'll
 *   have to set up your own {@link Printer} and use {@link AbstractPrinter#run(Runnable)} to run the DIFF.
 * </p>
 */
public
class Diff extends DocumentDiff {

    static { AssertionUtil.enableAssertionsForThisClass(); }

    private static final ExecutorService PARALLEL_EXECUTOR_SERVICE = new ScheduledThreadPoolExecutor(
        Runtime.getRuntime().availableProcessors() * 3,
        ThreadUtil.DAEMON_THREAD_FACTORY
    );

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
        UNIFIED,
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

    private Predicate<? super String>         pathPredicate    = PredicateUtil.always();
    private final List<Pattern>               equivalentPaths  = new ArrayList<Pattern>();
    private AbsentFileMode                    addedFileMode    = AbsentFileMode.REPORT;
    private AbsentFileMode                    deletedFileMode  = AbsentFileMode.REPORT;
    private boolean                           reportUnchangedFiles;
    private Predicate<? super String>         lookIntoFormat   = PredicateUtil.always();
    private DiffMode                          diffMode         = DiffMode.NORMAL;
    private ExceptionHandler<IOException>     exceptionHandler = ExceptionHandler.<IOException>defaultHandler();
    private boolean                           sequential;
    private boolean                           recurseSubdirectories = true;

    // SETTERS FOR THE VARIOUS CONFIGURATION PARAMETERS

    public void
    setRecurseSubdirectories(boolean value) { this.recurseSubdirectories = value; }

    public void
    setAddedFileMode(AbsentFileMode value) { this.addedFileMode = value; }

    public void
    setDeletedFileMode(AbsentFileMode value) { this.deletedFileMode = value; }

    public void
    setReportUnchangedFiles(boolean value) { this.reportUnchangedFiles = value; }

    public void
    setLookInto(Predicate<? super String> value) { this.lookIntoFormat = value; }

    public void // SUPPRESS CHECKSTYLE JavadocMethod
    setDiffMode(DiffMode value) {

        switch ((this.diffMode = value)) {
        case NORMAL:  this.setDocumentDiffMode(DocumentDiffMode.NORMAL);  break;
        case CONTEXT: this.setDocumentDiffMode(DocumentDiffMode.CONTEXT); break;
        case UNIFIED: this.setDocumentDiffMode(DocumentDiffMode.UNIFIED); break;
        default:      break;
        }
    }

    public void
    setExceptionHandler(ExceptionHandler<IOException> value) { this.exceptionHandler = value; }

    public void
    setSequential(boolean value) { this.sequential = value; }

    public void
    setPathPredicate(Predicate<? super String> pathPredicate) { this.pathPredicate = pathPredicate; }

    public void
    addEquivalentPath(Pattern path) { this.equivalentPaths.add(path); }

    /**
     * Creates two trees of directories, normal files, compressed files, archive files and archive entries, compares
     * them and reports all differences.
     */
    public long
    execute(URL resource1, URL resource2) throws IOException, InterruptedException {

        SquadExecutor<NodeWithPath> squadExecutor = new SquadExecutor<NodeWithPath>(
            this.sequential ? ConcurrentUtil.SEQUENTIAL_EXECUTOR_SERVICE : Diff.PARALLEL_EXECUTOR_SERVICE
        );

        ResourceProcessor<NodeWithPath> rp = this.resourceProcessor(squadExecutor);

        Printers.verbose("Scanning ''{0}''...", resource1);
        final NodeWithPath node1 = rp.process("", resource1);
        if (node1 == null) {
            Printers.error("\"" + resource1 + "\" does not exist or all subnodes are excluded");
            return 0;
        }

        Printers.verbose("Scanning ''{0}''...", resource2);
        final NodeWithPath node2 = rp.process("", resource2);
        if (node2 == null) {
            Printers.error("\"" + resource2 + "\" does not exist or all subnodes are excluded");
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

        long differenceCount = this.diff(node1, node2);

        Printers.verbose("{0,choice,0#No differences|1#1 difference|1<{0} differences} found.", differenceCount);

        return differenceCount;
    }

    /**
     * Compares (potentially compressed) content with (potentially compressed) content, or two trees of archive
     * entries, and reports all differences via {@link Printers#info(String)}.
     * <p>
     *   Example output (for "traditional" diff mode):
     * </p>
     * <pre>{@code
     * 2a3
     * > ADDED LINE
     * 5d5
     * < DELETED LINE
     * 8c8
     * < CHANGD LINE
     * ---
     * > CHANGED LINE
     * }</pre>
     *
     * @param opener1 Must produce a non-{@code null} {@link InputStream}
     * @param opener2 Must produce a non-{@code null} {@link InputStream}
     * @return        The number of differences found
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

        long differenceCount = this.diff(node1, node2);

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
    private ResourceProcessor<NodeWithPath>
    resourceProcessor(SquadExecutor<NodeWithPath> squadExecutor) {

        ArchiveCombiner<NodeWithPath> archiveEntryCombiner = new ArchiveCombiner<Diff.NodeWithPath>() {

            @Override @Nullable public NodeWithPath
            combine(String archivePath, List<NodeWithPath> archiveCombinables) {
                TreeSet<NodeWithPath> archiveNodes = new TreeSet<NodeWithPath>(Diff.this.normalizedPathComparator);
                for (NodeWithPath ac : archiveCombinables) {
                    if (ac != null) archiveNodes.add(ac);
                }
                return new ArchiveNode(archivePath, archiveNodes);
            }
        };

        return ResourceProcessings.recursiveCompressedAndArchiveResourceProcessor(
            this.lookIntoFormat,        // lookIntoFormat
            this.pathPredicate,         // pathPredicate
            null,                       // directoryMemberNameComparator
            this.recurseSubdirectories, // recurseSubdirectories
            archiveEntryCombiner,       // archiveEntryCombiner
            this.contentsProcessor(),   // normalContentsProcessor
            squadExecutor,              // squadExecutor
            this.exceptionHandler       // exceptionHandler
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
                    @Override public int    getCrc32() { return finalCrc;  }
                    @Override public String toString() { return path;      }
                };
            }
        };
        return ContentsProcessings.recursiveCompressedAndArchiveContentsProcessor(
            this.lookIntoFormat,
            this.pathPredicate,
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
    diff(NodeWithPath node1, final NodeWithPath node2) throws IOException {

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
                        Diff.this.reportFileAdded(path);
                        differenceCount[0] += Diff.this.diff(
                            "(missing)",        // path1
                            path,               // path2
                            InputStreams.EMPTY, // inputStream1
                            document.open()     // inputStream2
                        );
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
                        differenceCount[0] += Diff.this.diff(
                            document.getPath(), // path1
                            "(missing)",        // path2
                            document.open(),    // inputStream1
                            InputStreams.EMPTY  // inputStream2
                        );
                    }
                    break;

                case IGNORE:
                    ;
                    break;

                default:
                    throw new AssertionError();
                }
            }

            /**
             * @return The <var>node</var>, iff it is a {@link DocumentNode}, otherwise all the {@link DocumentNode}s
             *         <em>under</em> the <var>node</var>
             */
            private List<DocumentNode>
            getDocuments(NodeWithPath node) {

                Set<NodeWithPath> children = node.children();
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
                final DocumentNode document1 = (DocumentNode) node1;
                final DocumentNode document2 = (DocumentNode) node2;

                final String path1 = document1.getPath();
                final String path2 = document2.getPath();

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

                // At this point, the two files have PHYSICALLY different contents, but they may still be LOGICALLY
                // equal.

                if (Diff.this.diffMode == DiffMode.EXIST) {
                    Diff.this.reportFileUnchanged(path1, path2);
                } else {

                    try {

                        // We are about to DIFF the two documents. The problem is that "reportFileChange()" must be
                        // called BEFORE the first line of DIFF is printed. Thus, we set up a printer that delays the
                        // invocation of "reportFileChanged()" until the first INFO message is printed.
                        final AbstractPrinter cp = AbstractPrinter.getContextPrinter();
                        cp.redirect(Level.INFO, new Consumer<String>() {

                            boolean first = true;

                            @Override public void
                            consume(String message) {
                                if (this.first) {
                                    this.first = false;
                                    Diff.this.reportFileChanged(path1, path2);
                                    if (Diff.this.diffMode == DiffMode.BRIEF) throw Diff.TERMINATE;
                                }
                                cp.info(message);
                            }
                        }).run(new RunnableWhichThrows<IOException>() {

                            @Override public void
                            run() throws IOException {

                                // DIFF the two documents.
                                long dc = Diff.this.diff(path1, path2, document1.open(), document2.open());
                                if (dc == 0) {
                                    Diff.this.reportFileUnchanged(path1, path2);
                                } else {
                                    differenceCount[0] += dc;
                                }
                            }
                        });
                    } catch (RuntimeException re) {
                        if (re != Diff.TERMINATE) throw re;
                    }
                }
            }
        };

        treeComparator.compare(node1, node2);

        return differenceCount[0];
    }
    private static final RuntimeException TERMINATE = new RuntimeException() {
        private static final long               serialVersionUID = 1L;
        @Override public synchronized Throwable initCause(@Nullable Throwable cause) { return this; }
    };

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
            @Override public long getSize()  { return finalSize;  }
            @Override public int  getCrc32() { return finalCrc32; }
        };
    }
}
