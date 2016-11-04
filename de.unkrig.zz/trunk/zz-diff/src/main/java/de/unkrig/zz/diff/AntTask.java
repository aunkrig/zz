
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

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.regex.Pattern;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectComponent;
import org.apache.tools.ant.Task;

import de.unkrig.commons.file.ExceptionHandler;
import de.unkrig.commons.lang.protocol.RunnableWhichThrows;
import de.unkrig.commons.nullanalysis.Nullable;
import de.unkrig.commons.text.AbstractPrinter;
import de.unkrig.commons.text.Printer;
import de.unkrig.commons.text.Printers;
import de.unkrig.commons.text.ProxyPrinter;
import de.unkrig.commons.text.pattern.Glob;
import de.unkrig.commons.text.pattern.Pattern2;
import de.unkrig.zz.diff.Diff.AbsentFileMode;
import de.unkrig.zz.diff.Diff.DiffMode;
import de.unkrig.zz.diff.Diff.LineEquivalence;
import de.unkrig.zz.diff.Diff.Tokenization;

/**
 * Computes the differences between files, directory trees, archive file entries and compressed files, and prints them
 * in various formats.
 * <p>
 *   To use this task, add this to your ANT build script:
 * </p>
 * <pre>{@code
<taskdef
    classpath="path/to/zz-diff-x.y.z-jar-with-dependencies.jar"
    resource="antlib.xml"
/>
 * }</pre>
 */
public
class AntTask extends Task {

    private final Diff       diff = new Diff();
    @Nullable private String property;
    @Nullable private File   outputFile;
    @Nullable private File   file1, file2;

    /**
     * Representation of an element with an attribute named "pathRegex".
     */
    public static
    class Element__pathRegex { // SUPPRESS CHECKSTYLE TypeName

        @Nullable private Pattern pathRegex;

        /**
         * The regular expression against which the files' and archive entries' patches are matched.
         */
        public void setPathRegex(String regex) { this.pathRegex = Pattern.compile(regex); }
    }

    /**
     * Representation of an element with an attribute named "path" and one named "regex".
     */
    public static
    class Element__path_regex { // SUPPRESS CHECKSTYLE TypeName

        private Glob              path = Glob.ANY;
        @Nullable private Pattern regex;

        /**
         * The path glob that qualifies files and archive entries. Defaults to "any".
         */
        public void
        setPath(String glob) { this.path = Glob.compile(glob, Pattern2.WILDCARD | Glob.INCLUDES_EXCLUDES); }

        /**
         * The regular expression that is applied to each line of text.
         */
        public void
        setRegex(String regex) { this.regex = Pattern.compile(regex); }
    }

    /**
     * Whether to ignore whitespace differences.
     */
    public void
    setIgnoreWhitespace(boolean value) { this.diff.setIgnoreWhitespace(value); }

    /**
     * Argument of {@link #setAbsentFileMode(OldAbsentFileMode)}.
     *
     * @deprecated Only used by the deprecated {@link #setAbsentFileMode(OldAbsentFileMode)}.
     */
    @Deprecated public
    enum OldAbsentFileMode {

        /**
         * Report about added and deleted files and directories.
         */
        REPORT_AS_ADDED_OR_DELETED,

        /**
         * Compare added or deleted file with the empty document;
         * compare added and deleted directories with the empty directory.
         */
        COMPARE_ADDED_AND_DELETED_WITH_EMPTY,

        /**
         * Compare added files with the empty document;
         * compare added directories with the empty directory.
         * <p>
         * Report about deleted files and directories.
         */
        COMPARE_ADDED_WITH_EMPTY,
    }

    /**
     * @ant.defaultValue REPORT_AS_ADDED_OR_DELETED
     * @deprecated       Use {@link #setAddedFileMode(Diff.AbsentFileMode)} and {@link #setDeletedFileMode(Diff.AbsentFileMode)}
     *                   instead.
     */
    @Deprecated public void
    setAbsentFileMode(OldAbsentFileMode value) {
        switch (value) {

        case REPORT_AS_ADDED_OR_DELETED:
            this.setAddedFileMode(AbsentFileMode.REPORT);
            this.setDeletedFileMode(AbsentFileMode.REPORT);
            break;

        case COMPARE_ADDED_AND_DELETED_WITH_EMPTY:
            this.setAddedFileMode(AbsentFileMode.COMPARE_WITH_EMPTY);
            this.setDeletedFileMode(AbsentFileMode.COMPARE_WITH_EMPTY);
            break;

        case COMPARE_ADDED_WITH_EMPTY:
            this.setAddedFileMode(AbsentFileMode.COMPARE_WITH_EMPTY);
            this.setDeletedFileMode(AbsentFileMode.REPORT);
            break;

        default:
            throw new IllegalStateException(String.valueOf(value));
        }
    }

    /**
     * Configures how files are reported that are missing in {@link #setFile1(File)}.
     * <dl>
     *   <dt>{@link AbsentFileMode#REPORT}:</dt>
     *   <dd>
     *     Report about a added files and directories.
     *   </dd>
     *   <dt>{@link AbsentFileMode#COMPARE_WITH_EMPTY}:</dt>
     *   <dd>
     *     Compare each added file with the empty document; compare each added directory with the empty directory.
     *   </dd>
     *   <dt>{@link AbsentFileMode#IGNORE}:</dt>
     *   <dd>
     *     Print nothing.
     *   </dd>
     * </dl>
     *
     * @ant.defaultValue REPORT
     */
    public void
    setAddedFileMode(AbsentFileMode value) { this.diff.setAddedFileMode(value); }

    /**
     * Configures how files are reported that are missing in {@link #setFile2(File)}.
     * <dl>
     *   <dt>{@link AbsentFileMode#REPORT}:</dt>
     *   <dd>
     *     Report about a deleted files and directories.
     *   </dd>
     *   <dt>{@link AbsentFileMode#COMPARE_WITH_EMPTY}:</dt>
     *   <dd>
     *     Compare each deleted file with the empty document; compare each deleted directory with the empty directory.
     *   </dd>
     *   <dt>{@link AbsentFileMode#IGNORE}:</dt>
     *   <dd>
     *     Print nothing.
     *   </dd>
     * </dl>
     *
     * @ant.defaultValue REPORT
     */
    public void
    setDeletedFileMode(AbsentFileMode value) { this.diff.setDeletedFileMode(value); }

    /**
     * Whether to also report unchanged files.
     */
    public void
    setReportUnchangedFiles(boolean value) { this.diff.setReportUnchangedFiles(value); }

    /**
     * Look into compressed and archive contents if the format and the path match the given glob.
     * <p>
     *   Supported archive formats are: [cpio, zip, dump, jar, tar, ar, arj, 7z].
     * </p>
     * <p>
     *   Supported compression formats are: [snappy-raw, bzip2, gz, snappy-framed, pack200, xz, z, lzma].
     * </p>
     * <p>
     *   The default is too look into any recognized archive or compressed contents.
     * </p>
     * <p>
     *   Example:
     * </p>
     * <p>
     *   {@code lookInto="zip:**,tar:**,gz:**"}
     * </p>
     *
     * @ant.valueExplanation <var>format-glob</var>:<var>path-glob</var>
     */
    public void
    setLookInto(String value) {
        this.diff.setLookInto(Glob.compile(value, Glob.INCLUDES_EXCLUDES | Pattern2.WILDCARD));
    }

    /**
     * Whether to disassemble {@code .class} files on-the-fly before comparing them.
     */
    public void
    setDisassembleClassFiles(boolean value) { this.diff.setDisassembleClassFiles(value); }

    /**
     * Whether to suppress output of line numbers when disassembling {@code .class} files.
     */
    public void
    setDisassembleClassFilesButHideLines(boolean value) { this.diff.setDisassembleClassFilesButHideLines(value); }

    /**
     * Whether to suppress output of local variables' names when disassembling {@code .class} files.
     */
    public void
    setDisassembleClassFilesButHideVars(boolean value) { this.diff.setDisassembleClassFilesButHideVars(value); }

    /**
     * Encoding of the files being compared (defaults to default platform encoding).
     *
     * @ant.valueExplanation <a
     *               href="http://docs.oracle.com/javase/7/docs/api/java/nio/charset/Charset.html#standard">charset</a>
     */
    public void
    setEncoding(String value) { this.diff.setCharset(Charset.forName(value)); }

    /**
     * Configures the style of the generated output.
     * <dl>
     *   <dt>{@code EXIST}:</dt>
     *   <dd>
     *     Report only which files were added or deleted (do <em>not</em> report changed content).
     *   </dd>
     *   <dt>{@code BRIEF}:</dt>
     *   <dd>
     *     Report only which files were added, deleted or changed.
     *   </dd>
     *   <dt>{@code NORMAL}:</dt>
     *   <dd>
     *     Output "<a href="http://en.wikipedia.org/wiki/Diff_utility#Usage">normal diff format</a>".
     *   </dd>
     *   <dt>{@code CONTEXT}:</dt>
     *   <dd>
     *     Output "<a href="http://en.wikipedia.org/wiki/Diff_utility#Context_format">context diff format</a>".
     *   </dd>
     *   <dt>{@code UNIFIED}:</dt>
     *   <dd>
     *     Output "<a href="http://en.wikipedia.org/wiki/Diff_utility#Unified_format">unified diff format</a>".
     *   </dd>
     * </dl>
     *
     * @ant.defaultValue NORMAL
     */
    public void
    setDiffMode(DiffMode value) { this.diff.setDiffMode(value); }

    /**
     * Amount of "context", i.e. the number of lines before and after each difference; default is +- three lines (only
     * relevant for "context diff" and "unified diff" formats).
     *
     * @see #setDiffMode(Diff.DiffMode)
     */
    public void
    setContextSize(int n) { this.diff.setContextSize(n); }

    /**
     * Whether to continue with the next file when an error occurs.
     */
    public void
    setKeepGoing(boolean value) {
        this.diff.setExceptionHandler(
            value
            ? new ExceptionHandler<IOException>() {

                @Override public void
                handle(String path, IOException exception) {
                    AntTask.this.log(path + ": " + exception.getMessage(), Project.MSG_ERR);
                }

                @Override public void
                handle(String path, RuntimeException runtimeException) {
                    AntTask.this.log(path + ": " + runtimeException.getMessage(), Project.MSG_ERR);
                }
            }
            : ExceptionHandler.<IOException>defaultHandler()
        );
    }

    /**
     * Whether to scan directories strictly sequentially; "{@code false}" means to parallelize the directory scan in
     * several threads.
     */
    public void
    setSequential(boolean value) { this.diff.setSequential(value); }

    /**
     * <dl>
     *   <dt>{@code LINE}</dt>
     *   <dd>
     *     The unit of text to compare is the "line", i.e. the character sequence terminated by a line separator.
     *   </dd>
     *   <dt>{@code JAVA}</dt>
     *   <dd>
     *     The unit to compare is the Java&trade; token, i.e. the comparison is insensitive to white space and line
     *     wrapping.
     *   </dd>
     * </dl>
     *
     * @ant.defaultValue LINE
     */
    public void
    setTokenization(Tokenization value) { this.diff.setTokenization(value); }

    /**
     * Whether to ignore C-style comments ("<code>/* ... &#42;/</code>") when comparing. "{@code false}" means that
     * C-style comments are treated as Java&trade; tokens.
     * <p>
     *   Notice that "doc comments" ("<code>/** ... &#42;/</code>") are not regarded as C-style comments, and ignoring
     *   of doc comments is controlled by a separate attribute ({@link #setIgnoreDocComments(boolean)}).
     * </p>
     *
     * @see #setTokenization(Diff.Tokenization)
     */
    public void
    setIgnoreCStyleComments(boolean value) { this.diff.setIgnoreCStyleComments(value); }

    /**
     * Whether to ignore C++-style comments ("{@code // ...}") when comparing. "{@code false}" means that
     * C++-style comments are treated as Java&trade; tokens. Relevant iff {@link #setTokenization(Diff.Tokenization)}
     * is {@link Tokenization#JAVA JAVA}.
     *
     * @see #setTokenization(Diff.Tokenization)
     */
    public void
    setIgnoreCPlusPlusStyleComments(boolean value) { this.diff.setIgnoreCPlusPlusStyleComments(value); }

    /**
     * Whether to ignore doc comments ("<code>/** ... &#42;/</code>") when comparing. "{@code false}" means that doc
     * comments are treated as Java&trade; tokens.
     *
     * @see #setTokenization(Diff.Tokenization)
     */
    public void
    setIgnoreDocComments(boolean value) { this.diff.setIgnoreDocComments(value); }

    /**
     * Write the DIFF output to the given file instead of STDOUT.
     */
    public void
    setOut(File file) { this.outputFile = file; }

    /**
     * The first of the two files or the two directories to compare.
     */
    public void
    setFile1(File fileOrDirectory) { this.file1 = fileOrDirectory; }

    /**
     * The second of the two files or the two directories to compare.
     */
    public void
    setFile2(File fileOrDirectory) { this.file2 = fileOrDirectory; }

    /**
     * Set the named property to "{@code true}" iff there are no differences between {@link #setFile1(File)} and
     * {@link #setFile2(File)}.
     * <p>
     *   (Particularly useful with {@link #setDiffMode(Diff.DiffMode) diffMode}="{@link Diff.DiffMode#BRIEF QUIET}".
     * </p>
     *
     * @ant.valueExplanation property-name
     */
    public void
    setProperty(String value) { this.property = value; }

    /**
     * Compare only those documents who's pathes match the given path-glob.
     *
     * <p>
     *   The "path" is the path of each file pair, less the path of file1 and file2, plus, iff the file is compressed
     *   and/or an archive, the path within the file.
     * </p>
     * <table border="1">
     *   <caption>Examples</caption>
     *   <tr>
     *     <td>{@code !}</td>
     *     <td>The decompressed file.</td>
     *   </tr>
     *   <tr>
     *     <td>{@code !dir/file}</td>
     *     <td>Entry "{@code dir/file}" in the archive file.</td>
     *   </tr>
     *   <tr>
     *     <td>{@code !!dir/file}</td>
     *     <td>Entry "{@code dir/file}" in the compressed archive file.
     *   </tr>
     * </table>
     */
    public void
    setPath(String pathGlob) {
        this.diff.setPathPredicate(Glob.compile(pathGlob, Pattern2.WILDCARD | Glob.INCLUDES_EXCLUDES));
    }

    /**
     * Files with different pathes map iff their pathes match a regular expression, and all capturing groups are
     * equal.
     */
    public void
    addConfiguredEquivalentPath(Element__pathRegex element) {

        Pattern pathRegex = element.pathRegex;
        if (pathRegex == null) {
            throw new IllegalArgumentException("'nameRegex' attribute missing for <equivalentPath>");
        }

        this.diff.addEquivalentPath(pathRegex);
    }

    /**
     * Lines that contain matches of a regular expression, and all capturing groups are equal, are regarded as equal.
     * <p>
     *   Iff the {@link #setTokenization(Diff.Tokenization)} is different from {@link Tokenization#LINE}, then the
     *   equivalence check described before is executed on the scanned tokens instead.
     * </p>
     */
    public void
    addConfiguredEquivalentLine(Element__path_regex element) {

        Pattern regex = element.regex;
        if (regex == null) throw new IllegalArgumentException("'regex' attribute missing for <equivalentline>");

        this.diff.addEquivalentLine(new LineEquivalence(element.path, regex));
    }

    /**
     * Ignore differences where all lines (deleted, changed or added) match a regular expression.
     */
    public void
    addConfiguredIgnore(Element__path_regex element) {

        Pattern regex = element.regex;
        if (regex == null) throw new IllegalArgumentException("'regex' attribute missing for <ignore>");

        this.diff.addIgnore(new LineEquivalence(element.path, regex));
    }

    // End of ANT-related setters.

    @Override public void
    execute() {

        final File file1 = AntTask.this.file1;
        if (file1 == null) throw new IllegalArgumentException("'file1' attribute missing");
        final File file2 = AntTask.this.file2;
        if (file2 == null) throw new IllegalArgumentException("'file2' attribute missing");

        this.execute2(file1, file2);
    }

    /**
     * Wraps exceptions in ANT {@link BuildException}.
     */
    private void
    execute2(final File file1, final File file2) {

        try {
            this.execute3(file1, file2);
        } catch (Exception e) {
            throw new BuildException(e);
        }
    }

    private void
    execute3(final File file1, final File file2) throws Exception {

        AntTask.execute4(
            new RunnableWhichThrows<Exception>() {

                @Override public void
                run() throws Exception {

                    long differenceCount = AntTask.this.diff.execute(file1, file2);

                    String property = AntTask.this.property;
                    if (property != null && differenceCount == 0) {
                        AntTask.this.getProject().setProperty(property, "true");
                    }
                }
            },
            this.outputFile,
            this
        );
    }

    /**
     * Runs the given <var>runnable</var> with printers redirected to ANT's logging mechanism.
     */
    private static void
    execute4(RunnableWhichThrows<Exception> runnable, @Nullable File outputFile, final ProjectComponent component)
    throws Exception {

        Printer printer = new AbstractPrinter() {
            @Override public void warn(@Nullable String message)    { component.log(message, Project.MSG_WARN);    }
            @Override public void verbose(@Nullable String message) { component.log(message, Project.MSG_VERBOSE); }
            @Override public void info(@Nullable String message)    { component.log(message, Project.MSG_INFO);    }
            @Override public void error(@Nullable String message)   { component.log(message, Project.MSG_ERR);     }
            @Override public void debug(@Nullable String message)   { component.log(message, Project.MSG_DEBUG);   }
        };

        if (outputFile == null) {
            Printers.withPrinter(printer, runnable);
        } else {
            final PrintStream out = new PrintStream(outputFile);
            try {
                printer = new ProxyPrinter(printer) {
                    @Override public void info(@Nullable String message) { out.println(message); }
                };
                Printers.withPrinter(printer, runnable);
                out.close();
            } finally {
                try { out.close(); } catch (Exception e) {}
            }
        }
    }
}
