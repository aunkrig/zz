
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

package de.unkrig.zz.patch.diff;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import de.unkrig.commons.io.LineUtil;
import de.unkrig.commons.lang.AssertionUtil;
import de.unkrig.commons.nullanalysis.Nullable;
import de.unkrig.commons.text.StringStream;
import de.unkrig.commons.text.StringStream.UnexpectedElementException;
import de.unkrig.zz.patch.diff.DiffParser.LineChange.Mode;

/**
 * Parses a character sequence in various DIFF formats.
 */
public final
class DiffParser {

    static { AssertionUtil.enableAssertionsForThisClass(); }

    private DiffParser() {}

    /**
     * Representation of one line in a DIFF document.
     */
    public static
    class LineChange {

        /** The type of the diff line, specified by the character in column 1 */
        public enum Mode { CONTEXT, ADDED, DELETED }

        /** The type of the diff line, specified by the character in column 1 */
        public final Mode mode;

        /** The text of the diff line, less the prefix that indicates the mode, including the line separator. */
        public final String text;

        public
        LineChange(Mode mode, String text) {
            this.mode = mode;
            this.text = text;
        }

        @Override public String
        toString() {
            return this.mode + " " + this.text;
        }
    }

    /**
     * Representation of a "diff hunk", i.e. the description of textual changes in a sequence of lines.
     */
    public static
    class Hunk {

        /** Line number in first file where the hunk applies, counting from zero. */
        public final int from1;

        /** Line number in second file where the hunk applies, counting from zero. */
        public final int from2;

        /**
         * List of changes (CONTEXT, ADDED, DELETED) that defines the hunk.
         */
        public final List<LineChange> lineChanges;

        /**
         * @param from1       Line number in first file where the hunk applies, counting from zero
         * @param from2       Line number in second file where the hunk applies, counting from zero
         * @param lineChanges List of changes (CONTEXT, ADDED, DELETED) that defines the hunk.
         */
        public
        Hunk(int from1, int from2, List<LineChange> lineChanges) {
            this.from1       = from1;
            this.from2       = from2;
            this.lineChanges = lineChanges;
        }

        @Override public String
        toString() { return this.from1 + "/" + this.from2 + this.lineChanges; }
    }

    /**
     * A description of the differences of the contents of two files.
     */
    public static
    class Differential {

        /** The name of the first file. */
        @Nullable public final String fileName1;

        /** The name of the second file. */
        @Nullable public final String fileName2;

        /** The differences between the two files. */
        public final List<Hunk> hunks;

        public
        Differential(@Nullable String fileName1, @Nullable String fileName2, List<Hunk> hunks) {
            this.fileName1 = fileName1;
            this.fileName2 = fileName2;
            this.hunks     = hunks;
        }
    }

    /**
     * Parses a DIFF document from a file; a DIFF document generally contains differentials for one or more files.
     *
     * @see    DiffParser#parse(Reader)
     * @throws UnexpectedElementException  The contents read from the {@code file} is not a valid DIFF document
     */
    public static List<Differential>
    parse(File file, Charset charset) throws IOException, UnexpectedElementException {
        Reader r = new InputStreamReader(new FileInputStream(file), charset);
        try {
            return DiffParser.parse(r);
        } finally {
            try { r.close(); } catch (IOException ioe) {}
        }
    }

    /**
     * E.g. 'diff -r -w -c -C2 -I oioi kkk/foo2 kkk2/foo2'
     * <p>
     * Does not work for file names with spaces
     */
    private static final Pattern DIFF_LINE = Pattern.compile("diff.* \\S+ \\S+\\s*");

    /** E.g. '*** kkk/foo2    2011-04-06 19:00:20.234375000 +0200' */
    private static final Pattern
    CD_FILE_HEADER1 = Pattern.compile("\\*\\*\\* (.+?) +\\d\\d\\d\\d-\\d\\d-\\d\\d .*", Pattern.DOTALL);
    /** E.g. '--- kkk2/foo2   2011-04-14 15:09:44.500000000 +0200' */
    private static final Pattern
    CD_FILE_HEADER2 = Pattern.compile("--- (.+?) +\\d\\d\\d\\d-\\d\\d-\\d\\d .*", Pattern.DOTALL);
    /** E.g. '***************' */
    private static final Pattern
    CD_HUNK_SEPARATOR = Pattern.compile("\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\s*");
    /** E.g. '*** 3,6 ****' */
    private static final Pattern
    CD_HUNK_HEADER1 = Pattern.compile("\\*\\*\\* (\\d+),(\\d+) \\*\\*\\*\\*\\s*");
    /** E.g. '--- 3,8 ----' */
    private static final Pattern
    CD_HUNK_HEADER2 = Pattern.compile("--- (\\d+),(\\d+) ----\\s*");
    /** E.g. '+ XXXXXX' or '- YYYYYY' or '  ZZZZZZ' */
    private static final Pattern
    CD_LINE_CHANGE = Pattern.compile("([ \\-\\+!]) (.*)", Pattern.DOTALL);

    /** E.g. '4a5,6' or '15,16c17,18' or '18,33d19' */
    private static final Pattern
    TD_HUNK_HEADER = Pattern.compile("(\\d+)(?:,(\\d+))?([adc])(\\d+)(?:,(\\d+))?\\s*");
    /** E.g. '> XXXXXX' */
    private static final Pattern
    TD_LINE_ADDED = Pattern.compile("> (.*)", Pattern.DOTALL);
    /** E.g. '< YYYYYYYYYY' */
    private static final Pattern
    TD_LINE_DELETED = Pattern.compile("< (.*)", Pattern.DOTALL);
    /** '---' */
    private static final Pattern
    TD_SEPARATOR = Pattern.compile("---(.*)", Pattern.DOTALL);

    /** E.g. '--- kkk/foo2    2011-04-06 19:00:20.234375000 +0200' */
    private static final Pattern
    UD_FILE1_HEADER = Pattern.compile("--- (.+?) +\\d\\d\\d\\d-\\d\\d-\\d\\d .*", Pattern.DOTALL);
    /** E.g. '+++ kkk2/foo2   2011-04-14 15:09:44.500000000 +0200' */
    private static final Pattern
    UD_FILE2_HEADER = Pattern.compile("\\+\\+\\+ (.+?) +\\d\\d\\d\\d-\\d\\d-\\d\\d .*", Pattern.DOTALL);
    /** E.g. '@@ -12,9 +14,8 @@' */
    private static final Pattern
    UD_HUNK_HEADER = Pattern.compile("@@ -(\\d+),(\\d+) \\+(\\d+),(\\d+) @@\\s*");
    /** E.g. ' 14' or '-YYYYYYYYYY' or '+XXXXXXXXX' */
    private static final Pattern
    UD_LINE_CHANGE = Pattern.compile("([\\-\\+ ])(.*)", Pattern.DOTALL);

    /**
     * Parses a DIFF document; a DIFF document generally contains differentials for one or more files.
     * Each differential comprises a sequence of "hunks" in "traditional diff", "context diff" or "unified diff"
     * format.
     *
     * @throws UnexpectedElementException The contents read from {@code r} is not a valid DIFF document
     */
    public static List<Differential>
    parse(Reader r) throws DiffException, IOException, UnexpectedElementException {

        StringStream<IOException> ss = new StringStream<IOException>(LineUtil.readLineWithSeparator(r));

        List<Differential> differentials = new ArrayList<Differential>();
        while (!ss.atEnd()) {

            String fileName1 = null, fileName2 = null;

            if (ss.peekRead(DiffParser.DIFF_LINE)) {

                // 'diff -r -w -c -C2 -I oioi kkk/foo2 kkk2/foo2' <= DIFF_LINE
                fileName1 = ss.group(1);
                fileName2 = ss.group(2);
            }

            List<Hunk> hunks = new ArrayList<Hunk>();

            // Now guess the format and parse the hunks.

            // CONTEXT DIFF?

            if (ss.peek(DiffParser.CD_FILE_HEADER1)) {

                // '*** kkk/foo2    2011-04-06 19:00:20.234375000 +0200'   <= CD_FILE_HEADER1
                // '--- kkk2/foo2   2011-04-14 15:09:44.500000000 +0200'   <= CD_FILE_HEADER2
                // '***************'                                       <= CD_HUNK_SEPARATOR
                // '*** 3,6 ****'
                // '--- 3,8 ----'
                // '  3'
                // '  4'
                // '+ XXXXXX'
                // '+ YYYYYY'
                // '  5'
                // '  6'
                // '***************'                                       <= CD_HUNK_SEPARATOR
                // ...
                ss.read(DiffParser.CD_FILE_HEADER1);
                fileName1 = ss.group(1);
                ss.read(DiffParser.CD_FILE_HEADER2);
                fileName2 = ss.group(1);
                while (ss.peekRead(DiffParser.CD_HUNK_SEPARATOR)) hunks.add(DiffParser.parseContextDiffHunk(ss));
            } else
            if (ss.peek(DiffParser.CD_HUNK_SEPARATOR)) {

                // '***************'    <= CD_HUNK_SEPARATOR
                // '*** 3,6 ****'
                // '--- 3,8 ----'
                // '  3'
                // '  4'
                // '+ XXXXXX'
                // '+ YYYYYY'
                // '  5'
                // '  6'
                // '***************'    <= CD_HUNK_SEPARATOR
                // ...
                while (ss.peekRead(DiffParser.CD_HUNK_SEPARATOR)) hunks.add(DiffParser.parseContextDiffHunk(ss));
            } else

            // TRADITIONAL DIFF?

            if (ss.peek(DiffParser.TD_HUNK_HEADER)) {

                // '4a5,6'              <= TD_HUNK_HEADER
                // '> XXXXXX'
                // '> YYYYYY'
                // '15c17'              <= TD_HUNK_HEADER
                // '< YYYYYYYYYY'
                // '---'
                // '> XXXXXXXXXXXXXXXXXXXXX'
                // '18d19'              <= TD_HUNK_HEADER
                // '< ZZZZ'
                // ...
                do {
                    hunks.add(DiffParser.parseTraditionalDiffHunk(ss));
                } while (ss.peek(DiffParser.TD_HUNK_HEADER));
            } else

            // UNIFIED DIFF?

            if (ss.peek(DiffParser.UD_FILE1_HEADER)) {

                // '--- kkk/foo2    2011-04-06 19:00:20.234375000 +0200'  <= UD_FILE1_HEADER
                // '+++ kkk2/foo2   2011-04-14 15:09:44.500000000 +0200'  <= UD_FILE2_HEADER
                // '@@ -2,6 +2,8 @@'                                      <= UD_HUNK_HEADER
                // ' 2'
                // ' 3'
                // ' 4'
                // '+XXXXXX'
                // '+YYYYYY'
                // ' 5'
                // ' 6'
                // ' 7'
                // '@@ -12,9 +14,8 @@'                                    <= UD_HUNK_HEADER
                // ' 12'
                // ' 13'
                // ' 14'
                // '-YYYYYYYYYY'
                // '+XXXXXXXXXXXXXXXXXXXXX'
                // ' 15'
                // ' 16'
                // '-ZZZZ'
                // ' 17'
                // ' 18'
                // ...
                ss.read(DiffParser.UD_FILE1_HEADER);
                fileName1 = ss.group(1);
                assert fileName1 != null;

                ss.read(DiffParser.UD_FILE2_HEADER);
                fileName2 = ss.group(1);
                assert fileName2 != null;

                while (ss.peek(DiffParser.UD_HUNK_HEADER)) {
                    hunks.add(DiffParser.parseUnifiedDiffHunk(ss));
                }
            } else
            {
                throw new DiffException("Unknown DIFF format '" + ss.peek() + "'");
            }

            differentials.add(new Differential(fileName1, fileName2, hunks));
        }

        return differentials;
    }

    /**
     * Examples of UNIFIED DIFF hunks:
     * <pre>
     * @@ -2,6 +2,8 @@          <= UD_HUNK_HEADER
     *  2                       <= UD_LINE_CHANGE
     *  3                       <= UD_LINE_CHANGE
     *  4                       <= UD_LINE_CHANGE
     * +XXXXXX                  <= UD_LINE_CHANGE
     * +YYYYYY                  <= UD_LINE_CHANGE
     *  5                       <= UD_LINE_CHANGE
     *  6                       <= UD_LINE_CHANGE
     *  7                       <= UD_LINE_CHANGE
     *
     *
     * @@ -12,9 +14,8 @@        <= UD_HUNK_HEADER
     *  12                      <= UD_LINE_CHANGE
     *  13                      <= UD_LINE_CHANGE
     *  14                      <= UD_LINE_CHANGE
     * -YYYYYYYYYY              <= UD_LINE_CHANGE
     * +XXXXXXXXXXXXXXXXXXXXX   <= UD_LINE_CHANGE
     *  15                      <= UD_LINE_CHANGE
     *  16                      <= UD_LINE_CHANGE
     * -ZZZZ                    <= UD_LINE_CHANGE
     *  17                      <= UD_LINE_CHANGE
     *  18                      <= UD_LINE_CHANGE
     * </pre>
     *
     * @throws DiffException           The {@link StringStream} does not contain a valid unified diff hunk
     * @throws UnexpectedElementException The {@link StringStream} does not contain a valid unified diff hunk
     */
    private static Hunk
    parseUnifiedDiffHunk(StringStream<IOException> ss) throws IOException, DiffException, UnexpectedElementException {

        ss.read(DiffParser.UD_HUNK_HEADER);
        final int from1  = Integer.parseInt(ss.group(1)) - 1;
        final int count1 = Integer.parseInt(ss.group(2));
        final int from2  = Integer.parseInt(ss.group(3)) - 1;
        final int count2 = Integer.parseInt(ss.group(4));

        List<LineChange> lineChanges = new ArrayList<LineChange>();
        int              lines1      = 0, lines2 = 0;
        while (ss.peekRead(DiffParser.UD_LINE_CHANGE)) {
            String code = ss.group(1);
            assert code != null;
            String contents = ss.group(2);
            assert contents != null;
            switch (code.charAt(0)) {
            case '+':
                lineChanges.add(new LineChange(Mode.ADDED, contents));
                lines2++;
                break;
            case '-':
                lineChanges.add(new LineChange(Mode.DELETED, contents));
                lines1++;
                break;
            case ' ':
                lineChanges.add(new LineChange(Mode.CONTEXT, contents));
                lines1++;
                lines2++;
                break;
            default:
                throw new AssertionError();
            }
        }

        if (lines1 != count1) {
            throw new DiffException(
                "Unified diff hunk header says "
                + count1
                + " FILE1 lines, but "
                + lines1
                + " lines appear"
            );
        }
        if (lines2 != count2) {
            throw new DiffException(
                "Unified diff hunk header says "
                + count2
                + " FILE2 lines, but "
                + lines2
                + " lines appear"
            );
        }

        return new Hunk(from1, from2, lineChanges);
    }

    /**
     * Example of a CONTEXT DIFF hunk:
     * <pre>
     * *** 3,6 ****      <= CD_HUNK_HEADER1
     * --- 3,8 ----      <= CD_HUNK_HEADER2
     *   3               <= CD_LINE_CHANGE
     *   4               <= CD_LINE_CHANGE
     * + XXXXXX          <= CD_LINE_CHANGE
     * + YYYYYY          <= CD_LINE_CHANGE
     *   5               <= CD_LINE_CHANGE
     *   6               <= CD_LINE_CHANGE
     * ***************   <= CD_HUNK_SEPARATOR  (not part of the hunk)
     * </pre>
     *
     * @throws UnexpectedElementException The {@link StringStream} does not contain a valid context diff hunk
     */
    private static Hunk
    parseContextDiffHunk(StringStream<IOException> ss) throws DiffException, IOException, UnexpectedElementException {

        // Parse FILE1 part.
        ss.read(DiffParser.CD_HUNK_HEADER1);
        int file1From = Integer.parseInt(ss.group(1));
        int file1To   = Integer.parseInt(ss.group(2));

        final List<LineChange> lineChanges = new ArrayList<LineChange>();
        if (!ss.peek(DiffParser.CD_HUNK_HEADER2)) {
            for (int i = file1From; i <= file1To; i++) {
                ss.read(DiffParser.CD_LINE_CHANGE);
                String code = ss.group(1);
                assert code != null;
                String contents = ss.group(2);
                assert contents != null;
                switch (code.charAt(0)) {
                case ' ':
                    lineChanges.add(new LineChange(Mode.CONTEXT, contents));
                    break;
                case '-':
                    lineChanges.add(new LineChange(Mode.DELETED, contents));
                    break;
                case '!':
                    lineChanges.add(new LineChange(Mode.DELETED, contents));
                    lineChanges.add(null);
                    break;
                case '+':
                    throw new DiffException("Unexpected '+' line in FILE1 part of context diff hunk");
                default:
                    throw new AssertionError();
                }
            }
        }

        // Parse FILE2 part.
        ss.read(DiffParser.CD_HUNK_HEADER2);
        int file2From = Integer.parseInt(ss.group(1));
        int file2To   = Integer.parseInt(ss.group(2));

        if (ss.peek(DiffParser.CD_HUNK_SEPARATOR)) {
            for (LineChange lc : lineChanges) {
                if (lc == null) {
                    throw new DiffException("Context diff hunk has '!' if FILE1 part, but lacks the FILE2 part");
                }
            }
        } else {
            int idx = lineChanges.isEmpty() ? -1 : 0;
            for (int i = file2From; i <= file2To; i++) {
                ss.read(DiffParser.CD_LINE_CHANGE);
                String code = ss.group(1);
                assert code != null;
                String contents = ss.group(2);
                assert contents != null;
                switch (code.charAt(0)) {
                case ' ':
                    if (idx == -1) {
                        lineChanges.add(new LineChange(Mode.CONTEXT, contents));
                    } else {
                        for (; idx < lineChanges.size(); idx++) {
                            LineChange lc = lineChanges.get(idx);
                            if (lc == null || lc.mode != Mode.DELETED) break;
                        }
                        if (idx >= lineChanges.size()) throw new DiffException("Short FILE1 part");
                        if (lineChanges.get(idx) == null || !contents.equals(lineChanges.get(idx++).text)) {
                            throw new DiffException("Inconsistent FILE2 context line '" + contents + "'");
                        }
                    }
                    break;
                case '+':
                    if (idx == -1) {
                        lineChanges.add(new LineChange(Mode.ADDED, contents));
                    } else {
                        for (; idx < lineChanges.size(); idx++) {
                            LineChange lc = lineChanges.get(idx);
                            if (lc == null || lc.mode != Mode.DELETED) break;
                        }
                        if (idx > lineChanges.size()) throw new DiffException("Short FILE1 part");
                        lineChanges.add(idx++, new LineChange(Mode.ADDED, contents));
                    }
                    break;
                case '!':
                    if (idx == -1) throw new DiffException("Unexpected '!' line in context diff");
                    if (idx + 2 >= lineChanges.size()) throw new DiffException("Short FILE1 part");
                    {
                        LineChange lc = lineChanges.get(idx++);
                        if (lc == null) throw new DiffException("Unmatched '" + contents + "'");
                        if (lc.mode != Mode.DELETED) {
                            throw new DiffException("'" + contents + "' does not match '" + lc + "'");
                        }
                    }
                    {
                        LineChange lc = lineChanges.get(idx);
                        if (lc != null) throw new DiffException("'" + contents + "' does not match '" + lc + "'");
                    }
                    lineChanges.set(idx++, new LineChange(Mode.ADDED, contents));
                    break;
                case '-':
                    throw new DiffException("Unexpected '-' line in FILE2 part of context diff hunk");
                default:
                    throw new AssertionError();
                }
            }
            if (idx != -1 && idx != lineChanges.size()) {
                throw new DiffException("Short FILE2 part in context diff hunk");
            }
        }

        return new Hunk(file1From - 1, file2From - 1, lineChanges);
    }

    /**
     * Examples of TRADITIONAL DIFF hunks:
     * <pre>
     * 4a5,6                    <= TD_HUNK_HEADER
     * > XXXXXX                 <= TD_LINE_ADDED
     * > YYYYYY                 <= TD_LINE_ADDED
     *
     *
     * 15c17                    <= TD_HUNK_HEADER
     * < YYYYYYYYYY             <= TD_LINE_DELETED
     * ---
     * > XXXXXXXXXXXXXXXXXXXXX  <= TD_LINE_ADDED
     *
     *
     * 18d19                    <= TD_HUNK_HEADER
     * < ZZZZ                   <= TD_LINE_ADDED
     * </pre>
     *
     * @throws UnexpectedElementException The {@link StringStream} does not contain a valid traditional diff hunk
     */
    private static Hunk
    parseTraditionalDiffHunk(StringStream<IOException> ss)
    throws IOException, DiffException, UnexpectedElementException {

        ss.read(DiffParser.TD_HUNK_HEADER);

        String code = ss.group(3);
        assert code != null && code.length() == 1;

        int              from1, from2;
        List<LineChange> lineChanges = new ArrayList<LineChange>();
        switch (code.charAt(0)) {

        case 'a':
            from1 = Integer.parseInt(ss.group(1));
            from2 = Integer.parseInt(ss.group(4)) - 1;
            {
                if (ss.group(2) != null) throw new DiffException("Traditional diff ADD hunk has FILE1 range");
                int to2 = ss.group(5) == null ? from2 + 1 : Integer.parseInt(ss.group(5));
                for (int i = from2; i < to2; i++) {
                    ss.read(DiffParser.TD_LINE_ADDED);
                    String contents = ss.group(1);
                    assert contents != null;
                    lineChanges.add(new LineChange(Mode.ADDED, contents));
                }
            }
            break;

        case 'd':
            from1 = Integer.parseInt(ss.group(1)) - 1;
            from2 = Integer.parseInt(ss.group(4));
            {
                int to1 = ss.group(2) == null ? from1 + 1 : Integer.parseInt(ss.group(2));
                if (ss.group(5) != null) throw new DiffException("Traditional diff DELETE hunk has FILE2 range");
                for (int i = from1; i < to1; i++) {
                    ss.read(DiffParser.TD_LINE_DELETED);
                    String contents = ss.group(1);
                    assert contents != null;
                    lineChanges.add(new LineChange(Mode.DELETED, contents));
                }
            }
            break;

        case 'c':
            from1 = Integer.parseInt(ss.group(1)) - 1;
            from2 = Integer.parseInt(ss.group(4)) - 1;
            {
                final int to1 = ss.group(2) == null ? from1 + 1 : Integer.parseInt(ss.group(2));
                final int to2 = ss.group(5) == null ? from2 + 1 : Integer.parseInt(ss.group(5));

                for (int i = from1; i < to1; i++) {
                    ss.read(DiffParser.TD_LINE_DELETED);
                    String contents = ss.group(1);
                    assert contents != null;
                    lineChanges.add(new LineChange(Mode.DELETED, contents));
                }

                ss.read(DiffParser.TD_SEPARATOR);
                for (int i = from2; i < to2; i++) {
                    ss.read(DiffParser.TD_LINE_ADDED);
                    String contents = ss.group(1);
                    assert contents != null;
                    lineChanges.add(new LineChange(Mode.ADDED, contents));
                }
            }
            break;

        default:
            throw new AssertionError();
        }

        return new Hunk(from1, from2, lineChanges);
    }
}
