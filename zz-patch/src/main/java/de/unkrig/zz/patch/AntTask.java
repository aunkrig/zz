
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

package de.unkrig.zz.patch;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectComponent;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.Resource;
import org.apache.tools.ant.types.ResourceCollection;
import org.apache.tools.ant.types.resources.FileResource;

import de.unkrig.commons.file.contentstransformation.ContentsTransformer;
import de.unkrig.commons.file.filetransformation.FileTransformer;
import de.unkrig.commons.lang.protocol.Mappings;
import de.unkrig.commons.lang.protocol.Predicate;
import de.unkrig.commons.lang.protocol.PredicateUtil;
import de.unkrig.commons.lang.protocol.RunnableWhichThrows;
import de.unkrig.commons.nullanalysis.Nullable;
import de.unkrig.commons.text.AbstractPrinter;
import de.unkrig.commons.text.Printers;
import de.unkrig.commons.text.StringStream.UnexpectedElementException;
import de.unkrig.commons.text.expression.EvaluationException;
import de.unkrig.commons.text.expression.Expression;
import de.unkrig.commons.text.expression.ExpressionEvaluator;
import de.unkrig.commons.text.expression.ExpressionUtil;
import de.unkrig.commons.text.parser.ParseException;
import de.unkrig.commons.text.pattern.Glob;
import de.unkrig.commons.text.pattern.Pattern2;
import de.unkrig.zz.patch.diff.DiffParser.Hunk;

/**
 * Adds, removes, renames and/or changes the contents of files and archives.
 * <p>
 *   Implements the following features:
 * </p>
 * <ul>
 *   <li>
 *     Transforms regular files, directory trees, compressed files and entries in archive files, and also in nested
 *     archives
 *   </li>
 *   <li>Handles patch files in NORMAL, CONTEXT and UNIFIED diff format</li>
 *   <li>Replaces the contents of files from with that of an "update file"</li>
 *   <li>Does search-and-replace within files (SED like)</li>
 *   <li>Transform in-place as well as out-of-place</li>
 *   <li>Optionally keeps copies of the original files</li>
 *   <li>Adds, removes and renames files, directories and archive entries</li>
 * </ul>
 * <p>
 *   To use this task, add this to your ANT build script:
 * </p>
 * <pre>{@code
<taskdef
    classpath="path/to/zz-patch-x.y.z-jar-with-dependencies.jar"
    resource="antlib.xml"
/>
 * }</pre>
 */
public
class AntTask extends Task {

    private final Patch                    patch               = new Patch();
    private FileTransformer.Mode           mode                = FileTransformer.Mode.TRANSFORM;
    private final List<ResourceCollection> resourceCollections = new ArrayList<ResourceCollection>();
    @Nullable private File                 file;
    @Nullable private File                 tofile;
    @Nullable private File                 todir;

    /**
     * Configures how the files are processed.
     * <dl>
     *   <dt>{@code TRANSFORM}</dt>
     *   <dd>
     *     Execute the operation without previously checking if it actually changes any files.
     *   </dd>
     *   <dt>{@code CHECK}</dt>
     *   <dd>
     *     Execute the operation, but do not create or modify any files, and fail iff the operation does not produce
     *     an identical result. Since <zzpatch> is typically much cheaper in this mode than in mode TRANSFORM, it may
     *     be efficient to execute <zzpatch> in this mode first to check whether the transformation would modify and
     *     files, before executing it in TRANSFORM mode, particularly if you expect few or no modifications.
     *   <dd>
     *   <dt>{@code CHECK_AND_TRANSFORM}</dt>
     *   <dd>
     *     For in-place transformations: Before executing the actual transformation, verify that it will actually
     *     modify any files. Since checking whether a transformation would actually change any files is typically much
     *     cheaper than the executing the actual transformation, this mode may be more efficient than TRANSFORM mode,
     *     particularly if you expect few or no modifications.
     *   </dd>
     * </dl>
     *
     * @ant.defaultValue TRANSFORM
     */
    public void
    setMode(FileTransformer.Mode mode) { this.mode = mode; }

    /**
     * Whether to keep backup copies of files/entries that are modified or removed, renamed to {@code
     * ".}<var>file-name</var>{@code .orig"}.
     */
    public void
    setKeepOriginals(boolean value) { this.patch.setKeepOriginals(value); }

    /**
     * Configures a file to be transformed; either in-place, or, iff {@code tofile=...} or {@code todir=...} is
     * configured, out-of-place.
     */
    public void
    setFile(File file) { this.file = file; }

    /**
     * Configures the output file for the input file specified through {@code file=...}. The default is to patch the
     * input file in-place.
     */
    public void
    setTofile(File file) { this.tofile = file; }

    /**
     * Configures the directory for the output file created from {@code file=...} and/or the resource collection
     * subelements.
     */
    public void
    setTodir(File existingDir) { this.todir = existingDir; }

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
        this.patch.setLookIntoFormat(Glob.compile(value, Pattern2.WILDCARD | Glob.INCLUDES_EXCLUDES));
    }

    /**
     * Adds another set of resources ({@code <fileset>}, {@code <path>}, ...) that will be patched.
     */
    public void
    addConfigured(ResourceCollection value) { this.resourceCollections.add(value); }

    /**
     * Configures that the contents of files/entries that match the {@code name} glob pattern be replaced with the
     * contents of the given "update file".
     */
    public void
    addConfiguredUpdate(Element_path element) {

        if (element.path == PredicateUtil.<String>always()) {
            throw new BuildException("'name=<glob>=<update-file>' must be configured");
        }

        this.patch.addContentsTransformation(element.path, new UpdateContentsTransformer(element.path));
    }

    /**
     * Configures that lines that match the {@link SubstituteElement#setRegex(String)} within files/entries that match
     * the {@link SubstituteElement#setPath(String)} glob pattern be replaced with the {@link
     * SubstituteElement#setReplacement(String)} string.
     * <p>
     *   Alternatively, the regex and the replacement can be configured with {@link
     *   SubstituteElement#addConfiguredRegex(AntTask.TextElement)} and {@link
     *   SubstituteElement#addConfiguredReplacement(AntTask.TextElement)} subelements; one advantage of subelements is
     *   that they can contain {@code <![CDATA[...]]>} sections where you don't have to SGML-escape special characters.
     * </p>
     * <p>
     *   You are not limited to line-wise pattern matching, but be careful with using greedy quantifiers, because these
     *   may require that the entire contents of each file needs to be loaded into memory.
     * </p>
     */
    public void
    addConfiguredSubstitute(final SubstituteElement element) {

        this.addContentsTransformation(
            element.path,
            new SubstitutionContentsTransformer(
                element.inputCharset,
                element.outputCharset,
                Pattern.compile(element.getRegex(), Pattern.MULTILINE),
                element.getReplacement(),
                AntTask.expressionToSubstitutionCondition(element.condition)
            )
        );
    }

    private static SubstitutionContentsTransformer.Condition
    expressionToSubstitutionCondition(final Expression condition) {

        if (condition == Expression.TRUE)  return SubstitutionContentsTransformer.Condition.ALWAYS;
        if (condition == Expression.FALSE) return SubstitutionContentsTransformer.Condition.NEVER;

        return new SubstitutionContentsTransformer.Condition() {

            @Override public boolean
            evaluate(String path, CharSequence match, int occurrence) {
                try {
                    return ExpressionEvaluator.toBoolean(
                        condition.evaluate(Mappings.<String, Object>mapping(
                            "path",       path,      // SUPPRESS CHECKSTYLE Wrap:2
                            "match",      match,
                            "occurrence", occurrence
                        ))
                    );
                } catch (EvaluationException ee) {
                    throw new RuntimeException(ee);
                }
            }

            @Override public String
            toString() { return condition.toString(); }
        };
    }

    /**
     * Configures a substitution, i.e. the transformation of contents by finding pattern matches and replacing them
     * with a replacement string.
     */
    public static
    class SubstituteElement extends Element_path {

        private Charset          inputCharset  = Charset.defaultCharset();
        private Charset          outputCharset = Charset.defaultCharset();
        private Expression       condition     = Expression.TRUE;
        @Nullable private String regex, replacement;

        /**
         * The encoding of the transformation input; defaults to the platform default encoding.
         */
        public void setInputEncoding(String charset) { this.inputCharset = Charset.forName(charset); }

        /**
         * The encoding of the transformation output; defaults to the platform default encoding.
         */
        public void setOutputEncoding(String charset) { this.outputCharset = Charset.forName(charset); }

        /**
         * The regular expression the defines a match.
         * <p>
         *   For the precise description of the supported regular-expression constructs, see <a href="http://docs.oracl
         *e.com/javase/7/docs/api/java/util/regex/Pattern.html#sum">here</a>.
         * </p>
         */
        public void
        setRegex(String regex) {
            if (this.regex != null) {
                throw new BuildException("Only one of 'regex=...' and '<regex>' must be configured");
            }
            this.regex = regex;
        }

        /**
         * The regular expression the defines a match.
         * <p>
         *   For the precise description of the supported regular-expression constructs, see <a href="http://docs.oracl
         *e.com/javase/7/docs/api/java/util/regex/Pattern.html#sum">here</a>.
         * </p>
         */
        public void
        addConfiguredRegex(TextElement subelement) {
            if (this.regex != null) {
                throw new BuildException("Only one of 'regex=...' and '<regex>' must be configured");
            }
            this.regex = subelement.text;
        }

        /** Getter for the mandatory 'regex' attribute or subelement. */
        public String
        getRegex() {
            String regex = this.regex;
            if (regex == null) {
                throw new BuildException("A 'regex=\"...\"' attribute or a '<regex>' subelement must be configured");
            }
            return regex;
        }

        /**
         * The "replacement string" that determines how each match is replaced.
         * <p>
         *   For the precise description of the format, see <a href="http://docs.oracle.com/javase/7/docs/api/java/ut
         *il/regex/Matcher.html#appendReplacement%28java.lang.StringBuffer,%20java.lang.String%29">here</a>.
         * </p>
         */
        public void
        setReplacement(String replacementString) {
            if (this.replacement != null) {
                throw new BuildException("Only one of 'replacement=...' and '<replacement>' must be configured");
            }
            this.replacement = replacementString;
        }

        /**
         * The "replacement string" that determines how each match is replaced.
         * <p>
         *   For the precise description of the format, see <a href="http://docs.oracle.com/javase/7/docs/api/java/ut
         *il/regex/Matcher.html#appendReplacement%28java.lang.StringBuffer,%20java.lang.String%29">here</a>.
         * </p>
         */
        public void
        addConfiguredReplacement(TextElement subelement) {
            if (this.replacement != null) {
                throw new BuildException("Only one of 'replacement=...' and '<replacement>' must be configured");
            }
            this.replacement = subelement.text;
        }

        /**
         * Getter for the mandatory {@code replacement=...} attribute or {@code <replacement>} subelement.
         */
        public String
        getReplacement() {
            String replacement = this.replacement;
            if (replacement == null) {
                throw new BuildException(
                    "A 'replacement=\"...\"' attribute or a '<replacement>' subelement must be configured"
                );
            }
            return replacement;
        }

        /**
         * Configures a condition that must evaluate to {@code true} before each occurrence is replaced.
         * <p>
         *   The following variables are available in the expression:
         * </p>
         * <dl>
         *   <dt>{@code path}</dt>
         *   <dd>
         *     The path currently being patched.
         *   </dd>
         *   <dt>{@code match}</dt>
         *   <dd>
         *     The text of the match.
         *   </dd>
         *   <dt>{@code occurrence}</dt>
         *   <dd>
         *     The index of the occurrence within the document, starting at zero.
         *   </dd>
         * </dl>
         */
        public void
        setCondition(String expression) throws ParseException {
            this.condition = (
                new ExpressionEvaluator("path", "match", "occurrence").parse(expression)
            );
        }
    }

    /**
     * Configures a transformation by applying a DIFF document.
     * <p>
     *   A DIFF document generally contains differentials for one or more files. Each differential comprises a sequence
     *   of "hunks". <a href="http://en.wikipedia.org/wiki/Diff#Usage">Traditional format</a>, <a
     *   href="http://en.wikipedia.org/wiki/Diff#Context_format">context format</a> and <a
     *   href="http://en.wikipedia.org/wiki/Unified_diff#Unified_format">unified format</a> are supported.
     * </p>
     * <p>
     *   If the DIFF document describes more than on differential, then all but the first differential are ignored.
     *   The file name information in the differential is also ignored.
     * </p>
     *
     * @throws UnexpectedElementException The patch file does not contain a valid DIFF document
     */
    public void
    addConfiguredPatch(final PatchElement element)
    throws IOException, UnexpectedElementException {

        File patchFile = element.patchFile;
        if (patchFile == null) throw new BuildException("Attribute 'patchFile' must be set");

        this.addContentsTransformation(
            element.path,
            new PatchContentsTransformer(
                element.inputCharset,
                element.outputCharset,
                patchFile,
                element.patchFileCharset,
                new PatchContentsTransformer.Condition() {

                    @Override public boolean
                    evaluate(
                        String     path,
                        List<Hunk> hunks,
                        int        hunkIndex,
                        Hunk       hunk,
                        int        lineNumber
                    ) {
                        try {
                            return ExpressionEvaluator.toBoolean(
                                element.condition.evaluate(Mappings.<String, Object>mapping(
                                    "path",       path,      // SUPPRESS CHECKSTYLE Wrap:4
                                    "hunks",      hunks,
                                    "hunkIndex",  hunkIndex,
                                    "lineNumber", lineNumber
                                ))
                            );
                        } catch (EvaluationException ee) {
                            throw new RuntimeException(ee);
                        }
                    }
                }
            )
        );
    }

    /**
     * Configures a patch, i.e. the transformation of contents by applying a "DIFF document" to the input.
     */
    public static
    class PatchElement extends Element_path { // SUPPRESS CHECKSTYLE TypeName

        private Charset        inputCharset      = Charset.defaultCharset();
        private Charset        outputCharset     = Charset.defaultCharset();
        @Nullable private File patchFile;
        private Charset        patchFileCharset  = Charset.defaultCharset();
        private Expression     condition         = ExpressionUtil.constantExpression(Boolean.TRUE);

        /**
         * The encoding of the transformation input; defaults to the platform default encoding.
         */
        public void setInputEncoding(String value) { this.inputCharset = Charset.forName(value); }

        /**
         * The encoding of the transformation output; defaults to the platform default encoding.
         */
        public void setOutputEncoding(String value) { this.outputCharset = Charset.forName(value); }

        /**
         * The file that contains the DIFF document.
         */
        public void setPatchFile(File patchFile) { this.patchFile = patchFile; }

        /**
         * The encoding of the patch file; defaults to the platform default encoding.
         */
        public void setPatchFileEncoding(String charset) { this.patchFileCharset = Charset.forName(charset); }

        /**
         * Configures a condition that must evaluate to {@code true} before each DIFF hunk is applied.
         * <p>
         *   The following variables are available in the expression:
         * </p>
         * <dl>
         *   <dt>{@code path}</dt>
         *   <dd>
         *     The path currently being patched.
         *   </dd>
         *   <dt>{@code hunks}</dt>
         *   <dd>
         *     The hunks being applied.
         *   </dd>
         *   <dt>{@code hunkIndex}</dt>
         *   <dd>
         *     The index of the current hunk.
         *   </dd>
         *   <dt>{@code hunk}</dt>
         *   <dd>
         *     The current hunk.
         *   </dd>
         *   <dt>{@code lineNumber}</dt>
         *   <dd>
         *     The line number where the hunk starts.
         *   </dd>
         * </dl>
         */
        public void
        setCondition(String expression) throws ParseException {
            this.condition = (
                new ExpressionEvaluator("path", "hunks", "hunkIndex", "hunk", "lineNumber").parse(expression)
            );
        }
    }

    /**
     * Configures that files/entries that match the {@code name} be deleted/removed.
     */
    public void
    addConfiguredRemove(Element_path element) { this.patch.addRemoval(element.path); }

    /**
     * Configures that files/entries that match the {@code name} be renamed.
     */
    public void
    addConfiguredRename(Element_path2 element) { this.patch.addRenaming(element.path); }

    /**
     * Configures that an entry be added to all archives that match the path pattern.
     */
    public void
    addConfiguredAdd(AddElement element) {
        this.patch.addAddition(element.path, element.getEntryName(), element.getContents());
    }

    /**
     * Configures an "add" operation, i.e. an entry that is added to an archive.
     */
    public static
    class AddElement extends Element_path {

        @Nullable private String entryName;
        @Nullable private File   contents;

        /**
         * The name of the archive entry to add (may contain slashes).
         */
        public void
        setEntryName(String entryName) { this.entryName = entryName; }

        /**
         * @return The value of of the attribute '{@code entryName="..."}'
         */
        public String
        getEntryName() {
            String entryName = this.entryName;
            if (entryName == null) {
                throw new BuildException("Attribute 'entryName=\"...\"' must be set");
            }
            return entryName;
        }

        /**
         * The file that contains the contents for the new archive entry.
         */
        public void
        setContents(File contentsFile) { this.contents = contentsFile; }

        /**
         * @return The non-{@code null} value of the '{@code contents="..."}' attribute
         */
        public File
        getContents() {
            File contents = this.contents;
            if (contents == null) throw new BuildException("Attribute 'contents=\"...\"' must be set");
            return contents;
        }
    }

    /**
     * A predicate that determines whether a path is applicable by matching it with a glob.
     */
    public static
    class Element_path extends ProjectComponent { // SUPPRESS CHECKSTYLE TypeName

        protected int flags = Pattern2.WILDCARD | Glob.INCLUDES_EXCLUDES;

        /**
         * The glob that specifies the applicable pathes.
         */
        Glob path = Glob.ANY;

        /**
         * @deprecated Use {@link #setPath(String)} instead
         */
        @Deprecated public void
        setName(String path) { this.setPath(path); }

        /**
         * The glob to match the pathes against.
         */
        public void
        setPath(String glob) { this.path = Glob.compile(glob, this.flags); }
    }

    /**
     * A glob that implements renaming of a path.
     */
    public static
    class Element_path2 extends Element_path { // SUPPRESS CHECKSTYLE TypeName
        { this.flags |= Glob.REPLACEMENT; }
    }

    /***/
    public static
    class TextElement extends ProjectComponent {

        private String text = "";

        /** See ANT documentation. */
        public void
        addText(String text) { this.text += this.getProject().replaceProperties(text).trim(); }
    }

    private void
    addContentsTransformation(Predicate<String> pathPredicate, ContentsTransformer delegate) {

        this.patch.addContentsTransformation(pathPredicate, delegate);
    }

    @Override public void
    execute() throws BuildException {

        try {
            this.execute2();
        } catch (BuildException be) {
            throw be;
        } catch (Exception e) {
            throw new BuildException(e);
        }
    }

    private void
    execute2() throws Exception {

        AbstractPrinter printer = new AbstractPrinter() {
            @Override public void warn(@Nullable String message)    { AntTask.this.log(message, Project.MSG_WARN);    }
            @Override public void verbose(@Nullable String message) { AntTask.this.log(message, Project.MSG_VERBOSE); }
            @Override public void info(@Nullable String message)    { AntTask.this.log(message, Project.MSG_INFO);    }
            @Override public void error(@Nullable String message)   { AntTask.this.log(message, Project.MSG_ERR);     }
            @Override public void debug(@Nullable String message)   { AntTask.this.log(message, Project.MSG_DEBUG);   }
        };

        printer.run(new RunnableWhichThrows<Exception>() {
            @Override public void run() throws Exception { AntTask.this.execute3(); }
        });
    }

    private void
    execute3() throws Exception {

        FileTransformer fileTransformer = this.patch.fileTransformer(
            false, // lookIntoDirectories
            true   // renameOrRemoveTopLevelFiles
        );

        // Process 'file="..."' / 'tofile="..."' / 'todir="..."'.
        File file = this.file;
        if (file != null) {
            if (this.tofile != null && this.todir != null) {
                throw new BuildException(
                    "'tofile=\"...\"' and 'todir=\"...\"' must not be configured at the same time"
                );
            }

            File out = (
                this.tofile != null ? this.tofile :
                this.todir  != null ? new File(this.todir, file.getName()) :
                file
            );

            if (out.equals(file)) {
                Printers.verbose("Patching ''{0}'' in-place", file);
            } else {
                Printers.verbose("Patching ''{0}'' to ''{1}''", new Object[] { file, out });
            }

            fileTransformer.transform(file.getPath(), file, out, this.mode);
        } else
        if (this.tofile != null) {
            throw new BuildException(
                "'tofile=\"...\"' must only be configured in conjunction with 'file=\"...\"'"
            );
        }

        // Process resource collections / 'todir="..."'.
        for (ResourceCollection rc : this.resourceCollections) {
            for (@SuppressWarnings("unchecked") Iterator<Resource> it = rc.iterator(); it.hasNext();) {
                Resource resource = it.next();
                if (resource.isFilesystemOnly()) {
                    FileResource fileResource = (FileResource) resource;

                    File in  = fileResource.getFile();
                    File out = this.todir != null ? new File(this.todir, resource.getName()) : in;

                    Printers.verbose("Patching ''{0}'' to ''{1}''", new Object[] { in, out });
                    fileTransformer.transform(fileResource.getName(), in, out, FileTransformer.Mode.TRANSFORM);
                } else {
                    File out = new File(this.todir, resource.getName());

                    Printers.verbose("Patching ''{0}'' to ''{1}''", new Object[] { resource, out });
                    InputStream is = resource.getInputStream();
                    try {
                        OutputStream os = new FileOutputStream(out);
                        try {
                            this.patch.contentsTransformer().transform(resource.getName(), is, os);
                            os.close();
                        } finally {
                            try { os.close(); } catch (Exception e) {}
                        }
                        is.close();
                    } finally {
                        try { is.close(); } catch (Exception e) {}
                    }
                }
            }
        }
    }
}
