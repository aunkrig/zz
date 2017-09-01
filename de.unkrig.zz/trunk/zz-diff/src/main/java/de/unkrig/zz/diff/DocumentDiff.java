
/*
 * de.unkrig.diff - An advanced version of the UNIX DIFF utility
 *
 * Copyright (c) 2016, Arno Unkrig
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
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Checksum;

import org.incava.util.diff.Difference;

import de.unkrig.commons.io.ByteFilterInputStream;
import de.unkrig.commons.lang.AssertionUtil;
import de.unkrig.commons.lang.ExceptionUtil;
import de.unkrig.commons.lang.protocol.Predicate;
import de.unkrig.commons.nullanalysis.Nullable;
import de.unkrig.commons.text.AbstractPrinter;
import de.unkrig.commons.text.Printer;
import de.unkrig.commons.text.Printers;
import de.unkrig.commons.text.scanner.AbstractScanner.Token;
import de.unkrig.commons.text.scanner.JavaScanner;
import de.unkrig.commons.text.scanner.JavaScanner.TokenType;
import de.unkrig.commons.text.scanner.ScanException;
import de.unkrig.commons.text.scanner.ScannerUtil;
import de.unkrig.commons.text.scanner.StringScanner;

/**
 * Implementation of a document comparator, i.e. the core of the UNIX DIFF utility.
 * <p>
 *   It prints its output via the {@link Printers context printer}; if you want to modify the printing, then you'll
 *   have to set up your own {@link Printer} and use {@link AbstractPrinter#run(Runnable)} to run the DIFF.
 * </p>
 */
public
class DocumentDiff {

    static { AssertionUtil.enableAssertionsForThisClass(); }

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

//    private static final ExecutorService PARALLEL_EXECUTOR_SERVICE = new ScheduledThreadPoolExecutor(
//        Runtime.getRuntime().availableProcessors() * 3,
//        ThreadUtil.DAEMON_THREAD_FACTORY
//    );

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
    enum DocumentDiffMode {

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

    // Configuration parameters.

    /** The possible modes for tokenizing the documents to compare. */
    public enum Tokenization { LINE, JAVA }

    private final Collection<LineEquivalence> equivalentLines = new ArrayList<LineEquivalence>();
    private final Collection<LineEquivalence> ignores         = new ArrayList<LineEquivalence>();
    private boolean                           ignoreWhitespace;
    private boolean                           disassembleClassFiles;
    private boolean                           disassembleClassFilesVerbose;
    @Nullable private File                    disassembleClassFilesSourceDirectory;
    private boolean                           disassembleClassFilesButHideLines;
    private boolean                           disassembleClassFilesButHideVars;
    private boolean                           disassembleClassFilesSymbolicLabels;
    private Charset                           charset          = Charset.defaultCharset();
    private DocumentDiffMode                  documentDiffMode = DocumentDiffMode.NORMAL;
    private int                               contextSize      = 3;
    private Tokenization                      tokenization     = Tokenization.LINE;
    private boolean                           ignoreCStyleComments;
    private boolean                           ignoreCPlusPlusStyleComments;
    private boolean                           ignoreDocComments;

    // SETTERS FOR THE VARIOUS CONFIGURATION PARAMETERS

    public void
    setIgnoreWhitespace(boolean value) { this.ignoreWhitespace = value; }

    public void
    setDisassembleClassFiles(boolean value) { this.disassembleClassFiles = value; }

    /**
     * @param value Whether to include a constant pool dump, constant pool indexes, and hex dumps of all attributes
     *              in the disassembly output
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
     * @param value Whether source line numbers are suppressed in the disassembly (defaults to {@code false})
     */
    public void
    setDisassembleClassFilesButHideLines(boolean value) { this.disassembleClassFilesButHideLines = value; }

    /**
     * @param value Whether local variable names are suppressed in the disassembly (defaults to {@code false})
     */
    public void
    setDisassembleClassFilesButHideVars(boolean value) { this.disassembleClassFilesButHideVars = value; }

    /**
     * @param value Whether to use numeric labels ('#123') or symbolic labels /'L12') in the bytecode disassembly
     */
    public void
    setDisassembleClassFilesSymbolicLabels(boolean value) { this.disassembleClassFilesSymbolicLabels = value; }

    public void
    setCharset(Charset value) { this.charset = value; }

    public void
    setDocumentDiffMode(DocumentDiffMode value) { this.documentDiffMode = value; }

    /**
     * The number of (equal) lines before and after each change to report; defaults to 3.
     * <p>
     *   Only relevant for diff modes {@link DocumentDiffMode#UNIFIED} and {@link DocumentDiffMode#CONTEXT}.
     * </p>
     *
     * @see #setDocumentDiffMode(DocumentDiffMode)
     */
    public void
    setContextSize(int value) { this.contextSize = value; }

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
    addEquivalentLine(LineEquivalence lineEquivalence) { this.equivalentLines.add(lineEquivalence); }

    public void
    addIgnore(LineEquivalence lineEquivalence) { this.ignores.add(lineEquivalence); }
    // CHECKSTYLE MethodCheck:ON

    /**
     * Analyzes the contents of the two documents and reports that the contents is "equal" or has "changed", and,
     * in the latter case, prints the actual differences.
     * <p>
     *   The two input streams are closed in any case, even on abrupt completion.
     * </p>
     *
     * @param stream1 {@code null} means {@code path1} designates a 'deleted' file
     * @param stream2 {@code null} means {@code path2} designates an 'added' file
     * @return        The number of detected differences
     */
    public long
    diff(String path1, String path2, InputStream stream1, InputStream stream2) throws IOException {

        // Read the contents of the two pathes.
        Line[] lines1 = this.readAllLines(stream1, path1);
        Line[] lines2 = this.readAllLines(stream2, path2);

        Printers.verbose(
            "''{0}'' ({1} {1,choice,0#lines|1#line|1<lines}) vs. ''{2}'' ({3} {3,choice,0#lines|1#line|1<lines})",
            path1,
            lines1.length,
            path2,
            lines2.length
        );

        // Compute the contents differences.
        List<Difference> diffs = this.logicalDiff(lines1, lines2);

        Printers.verbose("{0} raw {0,choice,0#differences|1#difference|1<differences} found", diffs.size());

        if (diffs.isEmpty()) {

            // At this point, the two documents equal, honoring also the "line equivalences" and the "tokenization".
            return 0;
        }

        // Determine which of the "ignores" are effective for this path.
        final List<Pattern> effectiveIgnores = new ArrayList<Pattern>();
        for (LineEquivalence ignore : DocumentDiff.this.ignores) {
            if (ignore.pathPattern.evaluate(path1)) {
                effectiveIgnores.add(ignore.lineRegex);
            }
        }
        if (!effectiveIgnores.isEmpty()) {

            // Now remove all differences that are "ignorable".
            IGNORABLE:
            for (Iterator<Difference> it = diffs.iterator(); it.hasNext();) {
                Difference d = it.next();

                if (d.getDeletedStart() != Difference.NONE) {
                    for (int i = d.getDeletedStart(); i <= d.getDeletedEnd(); i++) {
                        if (!DocumentDiff.contains(lines1[i].text, effectiveIgnores)) continue IGNORABLE;
                    }
                }
                if (d.getAddedStart() != Difference.NONE) {
                    for (int i = d.getAddedStart(); i <= d.getAddedEnd(); i++) {
                        if (!DocumentDiff.contains(lines2[i].text, effectiveIgnores)) continue IGNORABLE;
                    }
                }
                it.remove();
            }
            Printers.verbose("Reduced to {0} non-ignorable differences", diffs.size());

            if (diffs.isEmpty()) {

                // At this point, the two documents equal, honoring also the "ignores".
                return 0;
            }
        }

        // Report the actual differences.
        switch (DocumentDiff.this.documentDiffMode) {

        case NORMAL:
            DocumentDiff.normalDiff(lines1, lines2, diffs);
            break;

        case CONTEXT:
            Printers.info("*** " + path1);
            Printers.info("--- " + path2);
            DocumentDiff.this.contextDiff(lines1, lines2, diffs);
            break;

        case UNIFIED:
            Printers.info("--- " + path1);
            Printers.info("+++ " + path2);
            DocumentDiff.this.unifiedDiff(lines1, lines2, diffs);
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
                        ? DocumentDiff.this.ignoreDocComments
                        : DocumentDiff.this.ignoreCStyleComments
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
                if (token.type == CXX_COMMENT) return !DocumentDiff.this.ignoreCPlusPlusStyleComments;

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
                DocumentDiff.toString(delStart, delEnd)
                + (delEnd == Difference.NONE ? "a" : addEnd == Difference.NONE ? "d" : "c")
                + DocumentDiff.toString(addStart, addEnd)
            );

            if (delEnd != Difference.NONE) {
                DocumentDiff.printLines(delStart, delEnd, "< ", lines1);
                if (addEnd != Difference.NONE) Printers.info("---");
            }
            if (addEnd != Difference.NONE) {
                DocumentDiff.printLines(addStart, addEnd, "> ", lines2);
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
                    int boc1 = Math.max(0, firstDifference.getDeletedStart() - DocumentDiff.this.contextSize);
                    int eoc1 = Math.min((
                        lastDifference.getDeletedEnd() == Difference.NONE
                        ? lastDifference.getDeletedStart() + DocumentDiff.this.contextSize - 1
                        : lastDifference.getDeletedEnd() + DocumentDiff.this.contextSize
                    ), lines1.length - 1);
                    Printers.info("*** " + DocumentDiff.toString(boc1, eoc1) + " ****");
                    for (Difference d : chunk) {
                        DocumentDiff.printLines(boc1, d.getDeletedStart() - 1, "  ", lines1);
                        if (d.getDeletedEnd() == Difference.NONE) {
                            boc1 = d.getDeletedStart();
                        } else {
                            DocumentDiff.printLines(
                                d.getDeletedStart(),
                                d.getDeletedEnd(),
                                d.getAddedEnd() == Difference.NONE ? "- " : "! ",
                                lines1
                            );
                            boc1 = d.getDeletedEnd() + 1;
                        }
                    }
                    DocumentDiff.printLines(boc1, eoc1, "  ", lines1);
                }

                // Print file two aggregated differences.
                {
                    int boc2 = Math.max(0, firstDifference.getAddedStart() - DocumentDiff.this.contextSize);
                    int eoc2 = Math.min((
                        lastDifference.getAddedEnd() == Difference.NONE
                        ? lastDifference.getAddedStart() + DocumentDiff.this.contextSize - 1
                        : lastDifference.getAddedEnd() + DocumentDiff.this.contextSize
                    ), lines2.length - 1);
                    Printers.info("--- " + DocumentDiff.toString(boc2, eoc2) + " ----");
                    for (Difference d : chunk) {
                        DocumentDiff.printLines(boc2, d.getAddedStart() - 1, "  ", lines2);
                        if (d.getAddedEnd() == Difference.NONE) {
                            boc2 = d.getAddedStart();
                        } else {
                            DocumentDiff.printLines(
                                d.getAddedStart(),
                                d.getAddedEnd(),
                                d.getDeletedEnd() == Difference.NONE ? "+ " : "! ",
                                lines2
                            );
                            boc2 = d.getAddedEnd() + 1;
                        }
                    }
                    DocumentDiff.printLines(boc2, eoc2, "  ", lines2);
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

                int boc1 = Math.max(0, firstDifference.getDeletedStart() - DocumentDiff.this.contextSize);
                int eoc1 = Math.min((
                    lastDifference.getDeletedEnd() == Difference.NONE
                    ? lastDifference.getDeletedStart() + DocumentDiff.this.contextSize - 1
                    : lastDifference.getDeletedEnd() + DocumentDiff.this.contextSize
                ), lines1.length - 1);
                int boc2 = Math.max(0, firstDifference.getAddedStart() - DocumentDiff.this.contextSize);
                int eoc2 = Math.min((
                    lastDifference.getAddedEnd() == Difference.NONE
                    ? lastDifference.getAddedStart() + DocumentDiff.this.contextSize - 1
                    : lastDifference.getAddedEnd() + DocumentDiff.this.contextSize
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
                    DocumentDiff.printLines(boc1, d.getDeletedStart() - 1, " ", lines1);
                    if (d.getDeletedEnd() == Difference.NONE) {
                        boc1 = d.getDeletedStart();
                    } else {
                        DocumentDiff.printLines(d.getDeletedStart(), d.getDeletedEnd(), "-", lines1);
                        boc1 = d.getDeletedEnd() + 1;
                    }
                    if (d.getAddedEnd() == Difference.NONE) {
                        boc2 = d.getAddedStart();
                    } else {
                        DocumentDiff.printLines(d.getAddedStart(), d.getAddedEnd(), "+", lines2);
                        boc2 = d.getAddedEnd() + 1;
                    }
                }
                DocumentDiff.printLines(boc1, eoc1, " ", lines1);
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
                if (lookahead.getDeletedStart() - afterDeletion <= 2 * DocumentDiff.this.contextSize) {
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
         * Formats a list of {@link Difference}s and prints them to {@link DocumentDiff#out}.
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
     * <p>
     *   Eventually closes the <var>inputStream</var>, even on abrupt completion.
     * </p>
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
            disassemblerByteFilter.setVerbose(this.disassembleClassFilesVerbose);
            disassemblerByteFilter.setSourceDirectory(this.disassembleClassFilesSourceDirectory);
            disassemblerByteFilter.setHideLines(this.disassembleClassFilesButHideLines);
            disassemblerByteFilter.setHideVars(this.disassembleClassFilesButHideVars);
            disassemblerByteFilter.setSymbolicLabels(this.disassembleClassFilesSymbolicLabels);
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
     * Representation of a line read from a stream. Honors {@link DocumentDiff#ignoreRegexes} and {@link
     * DocumentDiff#ignoreWhitespace}.
     */
    private
    class Line implements Checksummable {

        private final String  text;
        private byte[]        value;

        Line(String text, Collection<Pattern> equivalences) {
            this.text = text;

            if (DocumentDiff.this.ignoreWhitespace) {
                text = DocumentDiff.WHITESPACE_PATTERN.matcher(text).replaceAll(" ");
            }

            for (Pattern p : equivalences) {
                Matcher matcher = p.matcher(text);
                if (matcher.find()) {
                    if (matcher.groupCount() == 0) {
                        this.value = DocumentDiff.IGNORED_LINE;
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
            this.value = text.getBytes(Charset.forName("UTF-8"));
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
}
