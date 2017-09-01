
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
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.Resource;
import org.apache.tools.ant.types.ResourceCollection;
import org.apache.tools.ant.types.resources.FileProvider;

import de.unkrig.commons.file.contentsprocessing.ContentsProcessor;
import de.unkrig.commons.file.fileprocessing.FileProcessor;
import de.unkrig.commons.lang.protocol.ProducerWhichThrows;
import de.unkrig.commons.nullanalysis.Nullable;
import de.unkrig.commons.text.pattern.Glob;
import de.unkrig.commons.text.pattern.Pattern2;
import de.unkrig.zz.grep.Grep.Operation;

/**
 * Finds lines in files in directory trees, archives an compressed files by regular expressions.
 * <p>
 *   To use this task, add this to your ANT build script:
 * </p>
 * <pre>{@code
<taskdef
    classpath="path/to/zz-grep-x.y.z-jar-with-dependencies.jar"
    resource="antlib.xml"
/>
 * }</pre>
 */
public
class AntTask extends Task {

    private static final Logger LOGGER = Logger.getLogger(Task.class.getName());

    private final Grep grep = new Grep();

    @Nullable private File                 file;
    @Nullable private String               path;
    @Nullable private String               regex;
    @Nullable private Boolean              caseSensitive;
    @Nullable private String               property;
    private final List<ResourceCollection> resourceCollections = new ArrayList<ResourceCollection>();

    // BEGIN CONFIGURATION SETTERS

    /** Another file that will be searched. */
    public void
    setFile(File file) { this.file = file; }

    /**
     * Look into archive files, nested archives, compressed files and nested compressed contents iff
     * "<var>format</var>:<var>path</var>" matches the given glob.
     * <p>
     *   The default is to look into any archive files, nested archives, compressed files and nested compressed
     *   contents.
     * </p>
     *
     * @ant.valueExplanation <var>format-glob</var>:<var>path-glob</var>
     */
    public void
    setLookInto(String value) {
        this.grep.setLookIntoFormat(Glob.compile(value, Pattern2.WILDCARD | Glob.INCLUDES_EXCLUDES));
    }

    /**
     * The charset to use when reading files and nested contents.
     * <p>
     *   Default is the "platform default encoding".
     * </p>
     *
     * @ant.valueExplanation charset
     */
    public void
    setEncoding(String value) { this.grep.setCharset(Charset.forName(value)); }

    /**
     * <dl>
     *   <dt>NORMAL</dt>
     *   <dd>
     *     For each match, print the file name, a colon, a space and the matched line.
     *   </dd>
     *   <dt>LIST</dt>
     *   <dd>
     *     For each match, print the file name.
     *   </dd>
     *   <dt>QUIET</dt>
     *   <dd>
     *     Do not print anything.
     *   </dd>
     * </dl>
     */
    public void
    setOperation(Operation value) { this.grep.setOperation(value); }

    /**
     * Whether matching lines should be treated as non-matching, and vice versa.
     */
    public void
    setInverted(boolean value) { this.grep.setInverted(value); }

    /**
     * Whether to disassemble Java class files on-the-fly before matching its contents.
     */
    public void
    setDisassembleClassFiles(boolean value) { this.grep.setDisassembleClassFiles(value); }

    /**
     * Whether to include a constant pool dump, constant pool indexes, and hex dumps of all attributes in the
     * disassembly output.
     */
    public void
    setDisassembleClassFilesVerbose(boolean value) { this.grep.setDisassembleClassFilesVerbose(value); }

    /**
     * Where to look for source files when disassembling .class files; {@code null} disables source file loading. Source
     * file loading is disabled by default.
     */
    public void
    setDisassembleClassFilesSourceDirectory(@Nullable File value) {
        this.grep.setDisassembleClassFilesSourceDirectory(value);
    }

    /**
     * Whether to hide source line numbers in the Java class file disassembly.
     */
    public void
    setDisassembleClassFilesButHideLines(boolean value) { this.grep.setDisassembleClassFilesButHideLines(value); }

    /**
     * Whether to remove local variable names from the Java class file disassembly.
     */
    public void
    setDisassembleClassFilesButHideVars(boolean value) { this.grep.setDisassembleClassFilesButHideVars(value); }

    /**
     * Whether to use numeric labels ('#123') or symbolic labels /'L12') in the bytecode disassembly.
     */
    public void
    setDisassembleClassFilesSymbolicLabels(boolean value) {
        this.grep.setDisassembleClassFilesSymbolicLabels(value);
    }

    /**
     * @deprecated Use {@link #setPath(String)} instead
     */
    @Deprecated public void
    setName(String value) { this.setPath(value); }

    /**
     * Search for matches only in files / entries who's path match the glob.
     */
    public void
    setPath(String pathGlob) { this.path = pathGlob; }

    /**
     * The regular expression to match each line of input against.
     */
    public void
    setRegex(String regex) { this.regex = regex; }

    /**
     * Whether the {@link #setRegex(String)} should be applied case-sensitively.
     *
     * @ant.defaultValue true
     */
    public void
    setCaseSensitive(boolean value) { this.caseSensitive = value; }

    /**
     * If set, then set the named property to "true" iff there is at least one match.
     */
    public void
    setProperty(String propertyName) { this.property = propertyName; }

    /***/
    public static
    class Element_path_regex_caseSensitive { // SUPPRESS CHECKSTYLE TypeName

        @Nullable private String path;
        @Nullable private String regex;
        private boolean          caseSensitive = true;

        /**
         * @deprecated Use {@link #setPath(String)} instead
         */
        @Deprecated public void
        setName(String value) { this.setPath(value); }

        /**
         * Search for matches only in files / entries who's path match the glob.
         */
        public void
        setPath(String glob) { this.path = glob; }

        /**
         * The regular expression to match each line of input against.
         */
        public void
        setRegex(String regex) { this.regex = regex; }

        /**
         * Whether the regex should be applied case-sensitively.
         *
         * @ant.defaultValue true
         */
        public void
        setCaseSensitive(boolean value) { this.caseSensitive = value; }
    }

    /**
     * In addition to the search configured by {@link #setName(String)}, {@link #setRegex(String)} and {@link
     * #setCaseSensitive(boolean)} attributes of the task (explained above), execute another search.
     */
    public void
    addConfiguredSearch(Element_path_regex_caseSensitive element) {
        if (this.path != null || this.regex != null || this.caseSensitive != null) {
            throw new BuildException(
                "'path=\"...\"', 'regex=\"...\"' and 'caseSensitive=\"...\"' are mutually exclusive "
                + "with '<search>' subelements"
            );
        }

        final String path  = element.path;
        final String regex = element.regex;
        if (regex == null) throw new BuildException("Attribute 'regex' must be set");

        this.grep.addSearch(
            path == null ? Glob.ANY : Glob.compile(path, Pattern2.WILDCARD | Glob.INCLUDES_EXCLUDES),
            regex,
            element.caseSensitive
        );
    }

    /** Another set of files or directory trees to search. */
    public void
    addConfigured(ResourceCollection value) { this.resourceCollections.add(value); }

    // END CONFIGURATION SETTERS

    /**
     * The ANT task "execute" method.
     *
     * @see Task#execute
     */
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

        {
            String  regex         = this.regex;
            String  path          = this.path;
            Boolean caseSensitive = this.caseSensitive;

            if (regex != null) {
                this.grep.addSearch(
                    path == null ? Glob.ANY : Glob.compile(path, Pattern2.WILDCARD | Glob.INCLUDES_EXCLUDES),
                    regex,
                    caseSensitive == null ? true : caseSensitive
                );
            } else
            if (path != null || caseSensitive != null) {
                throw new BuildException(
                    "'path=\"...\"' and 'caseSensitive=\"...\"' don't make sense without 'regex=\"...\"'"
                );
            }
        }

        ContentsProcessor<Void> contentsProcessor = this.grep.contentsProcessor();
        FileProcessor<Void>     fileProcessor     = this.grep.fileProcessor(
            false // lookIntoDirectories    We don't want directory traversal - ANT already did this for us
        );

        // Process 'file="..."'.
        {
            File file = this.file;

            if (file != null) {
                AntTask.LOGGER.log(Level.CONFIG, "Grepping ''{0}''", file.getPath());
                fileProcessor.process(file.getPath(), file);
            }
        }

        // Process resource collections.
        for (ResourceCollection resourceCollection : this.resourceCollections) {

            // Process each resource of each collection.
            for (
                @SuppressWarnings("unchecked") Iterator<Resource> it = resourceCollection.iterator();
                it.hasNext();
            ) {
                final Resource resource = it.next();

                String path = resource.getName();

                AntTask.LOGGER.log(Level.CONFIG, "Grepping ''{0}''", path);

                if (resource.isFilesystemOnly()) {

                    // Grep file resource.
                    fileProcessor.process(path, ((FileProvider) resource).getFile());
                } else {

                    // Grep non-file resource.
                    InputStream is = resource.getInputStream();
                    try {
                        contentsProcessor.process(
                            path,                                                 // path
                            is,                                                   // inputStream
                            resource.getSize(),                                   // size
                            -1L,                                                  // crc32
                            new ProducerWhichThrows<InputStream, IOException>() { // opener

                                @Override @Nullable public InputStream
                                produce() throws IOException { return resource.getInputStream(); }
                            }
                        );
                        is.close();
                    } finally {
                        try { is.close(); } catch (Exception e) {}
                    }
                }
            }
        }

        if (this.property != null && this.grep.getLinesSelected()) {
            this.getProject().setProperty(this.property, "true");
        }
    }
}
