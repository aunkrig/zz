
/*
 * de.unkrig.find - An advanced version of the UNIX FIND utility
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

package de.unkrig.zz.find;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectComponent;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.MacroDef;
import org.apache.tools.ant.taskdefs.MacroDef.NestedSequential;
import org.apache.tools.ant.taskdefs.MacroInstance;
import org.apache.tools.ant.types.Resource;
import org.apache.tools.ant.types.ResourceCollection;
import org.apache.tools.ant.types.resources.FileProvider;
import org.apache.tools.ant.types.resources.FileResource;

import de.unkrig.commons.lang.protocol.Consumer;
import de.unkrig.commons.lang.protocol.ConsumerWhichThrows;
import de.unkrig.commons.lang.protocol.Mapping;
import de.unkrig.commons.lang.protocol.Predicate;
import de.unkrig.commons.lang.protocol.RunnableWhichThrows;
import de.unkrig.commons.nullanalysis.Nullable;
import de.unkrig.commons.text.AbstractPrinter;
import de.unkrig.commons.text.pattern.Glob;
import de.unkrig.commons.text.pattern.Pattern2;
import de.unkrig.zz.find.Find.Action;
import de.unkrig.zz.find.Find.AndTest;
import de.unkrig.zz.find.Find.CatAction;
import de.unkrig.zz.find.Find.ChecksumAction;
import de.unkrig.zz.find.Find.ChecksumAction.ChecksumType;
import de.unkrig.zz.find.Find.CommaTest;
import de.unkrig.zz.find.Find.CopyAction;
import de.unkrig.zz.find.Find.DeleteAction;
import de.unkrig.zz.find.Find.DigestAction;
import de.unkrig.zz.find.Find.DisassembleAction;
import de.unkrig.zz.find.Find.EchoAction;
import de.unkrig.zz.find.Find.ExecAction;
import de.unkrig.zz.find.Find.ExecutabilityTest;
import de.unkrig.zz.find.Find.Expression;
import de.unkrig.zz.find.Find.LsAction;
import de.unkrig.zz.find.Find.ModificationTimeTest;
import de.unkrig.zz.find.Find.NameTest;
import de.unkrig.zz.find.Find.NotExpression;
import de.unkrig.zz.find.Find.OrTest;
import de.unkrig.zz.find.Find.PathTest;
import de.unkrig.zz.find.Find.PipeAction;
import de.unkrig.zz.find.Find.PrintAction;
import de.unkrig.zz.find.Find.PruneAction;
import de.unkrig.zz.find.Find.ReadabilityTest;
import de.unkrig.zz.find.Find.SizeTest;
import de.unkrig.zz.find.Find.Test;
import de.unkrig.zz.find.Find.TypeTest;
import de.unkrig.zz.find.Find.WritabilityTest;

/**
 * Recurses through a set of directories, files, archive files and nested archives and executes a set of tests and
 * actions for each file and archive entry.
 * <p>
 *   The execution of tests and actions stops when one of them evaluates to {@code false}, i.e. they are implicitly
 *   AND-related.
 * </p>
 * <p>
 *   To use this task, add this to your ANT build script:
 * </p>
 * <pre>{@code
<taskdef
    classpath="path/to/zz-find-x.y.z-jar-with-dependencies.jar"
    resource="antlib.xml"
/>
 * }</pre>
 *
 * @ant.subelementOrder inheritedFirst
 */
public
class AntTask extends AbstractElementWithOperands {

    private final Find                     find                = new Find();
    private final AndElement               root                = new AndElement();
    @Nullable private File                 outputFile;
    private final List<ResourceCollection> resourceCollections = new ArrayList<ResourceCollection>();


    /**
     * @ant.typeGroupSubdir  findExpressions
     * @ant.typeGroupName    FIND expression
     * @ant.typeGroupHeading FIND expressions
     * @ant.typeTitleMf      &lt;{0}&gt;
     * @ant.typeHeadingMf    <code>&lt;{0}&gt;</code>
     */
    public
    interface ExpressionElement {

        /**
         * Produces a FIND {@link Expression}.
         */
        Expression toExpression();
    }

    /**
     * Copies the contents of the current file to STDOUT and evaluates to TRUE.
     */
    public static
    class CatElement implements ExpressionElement {

        @Override public Expression
        toExpression() { return new CatAction(System.out); }
    }

    /**
     * Calculates a "checksum" of the contents, prints it and returns true.
     */
    public static
    class ChecksumElement implements ExpressionElement {

        private final Project    project;
        private ChecksumType     type = ChecksumAction.ChecksumType.CRC32;
        @Nullable private String propertyName;

        public
        ChecksumElement(Project project) { this.project = project; }

        /**
         * The checksum type to use
         *
         * @ant.defaultValue CRC32
         */
        public void
        setType(ChecksumAction.ChecksumType type) { this.type = type; }

        /**
         * The property to set
         *
         * @ant.defaultValue Print the checksum to STDOUT, instead of setting a property
         */
        public void
        setProperty(String propertyName) { this.propertyName = propertyName; }

        @Override public Expression
        toExpression() {

            return AntTask.redirectInfoToProperty(
                this.project,
                new ChecksumAction(this.type),
                this.propertyName
            );
        }
    }

    /**
     * Copies the contents of the current file to the named file and evaluates to TRUE.
     */
    public static
    class CopyElement implements ExpressionElement {

        @Nullable private File tofile;
        private boolean        mkdirs;

        /**
         * The file to copy to.
         *
         * @ant.mandatory
         */
        public void
        setTofile(File value) { this.tofile = value; }

        /**
         * Whether to create any missing parent directories for the output file.
         */
        public void
        setMkdirs(boolean value) { this.mkdirs = value; }

        @Override public Expression
        toExpression() {
            File tofile = this.tofile;
            if (tofile == null) throw new BuildException("Attribute 'tofile=\"<file>\"' not set");
            return new CopyAction(tofile, this.mkdirs);
        }
    }

    /**
     * Calculates a "message digest" of the contents, prints it and returns true.
     */
    public static
    class DigestElement implements ExpressionElement {

        private final Project    project;
        private String           algorithm = "MD5";
        @Nullable private String propertyName;

        public
        DigestElement(Project project) { this.project = project; }

        /**
         * The algorithm to use.
         *
         * @ant.defaultValue "MD5"
         */
        public void
        setAlgorithm(String algorithm) { this.algorithm = algorithm; }

        /**
         * The property to set.
         *
         * @ant.defaultValue Print the checksum to STDOUT, instead of setting a property
         */
        public void
        setProperty(String propertyName) { this.propertyName = propertyName; }

        @Override public Expression
        toExpression() {
            return AntTask.redirectInfoToProperty(this.project, new DigestAction(this.algorithm), this.propertyName);
        }
    }

    /**
     * Disassembles a Java .class file to STDOUT or a given file and evaluates to {@code true}.
     */
    public static
    class DisassembleElement implements ExpressionElement {

        private boolean        verbose;
        @Nullable private File sourceDirectory;
        private boolean        hideLines;
        private boolean        hideVars;
        private boolean        useSymbolicLabels;
        @Nullable private File toFile;

        /**
         * Whether to include a constant pool dump, constant pool indexes, and hex dumps of all attributes in the
         * disassembly output.
         */
        public void
        setVerbose(boolean value) { this.verbose = value;  }

        /**
         * Where to look for source files when disassembling .class files; {@code null} disables source file loading.
         * Source file loading is disabled by default.
         */
        public void
        setSourceDirectory(@Nullable File value) {
            this.sourceDirectory = value;
        }

        /**
         * Whether to suppress line numbers in the disassembly output.
         */
        public void
        setHidesLines(boolean hideLines) { this.hideLines = hideLines; }

        /**
         * Whether to suppress local variable names in the disassembly output.
         */
        public void
        setHidesVars(boolean hideLines) { this.hideLines = hideLines; }

        /**
         * Whether to use numeric labels ('#123') or symbolic labels /'L12') in the bytecode disassembly.
         */
        public void
        setUseSymbolicLabels(boolean value) { this.useSymbolicLabels = value; }

        /**
         * The file to redirect the disassembly output to.
         *
         * @ant.defaultValue Standard output
         */
        public void
        setToFile(File toFile) { this.toFile = toFile; }

        @Override public Expression
        toExpression() {
            return new DisassembleAction(
                this.verbose,
                this.sourceDirectory,
                this.hideLines,
                this.hideVars,
                this.useSymbolicLabels,
                this.toFile
            );
        }
    }

    /**
     * Executes an external command; the special string '{}' within the command is replaced with the full path of the
     * current file/directory/archive entry.
     * <p>
     *   Evaluates to {@code true} iff the command exists with status code '0'.
     * </p>
     */
    public static
    class ExecElement implements ExpressionElement {

        @Nullable private String command;

        /**
         * The command to execute; program name and command line arguments separated by whitespace.
         *
         * @ant.mandatory
         */
        public void
        setCommand(String command) { this.command = command; }

        @Override public Expression
        toExpression() {
            String command = this.command;
            if (command == null) throw new BuildException("Attribute 'command' must be set");
            return new ExecAction(Arrays.asList(command.split("\\s+")));
        }
    }

    /**
     * Prints the file type ("{@code d}" or "{@code -}"), readability ("{@code r}" or "{@code -}"), writability ("{@code
     * w}" or "{@code -}"), executability ("{@code x}" or "{@code -}"), size, modification time and path, and evaluates
     * to {@code true}.
     */
    public static
    class LsElement implements ExpressionElement {
        @Override public Expression toExpression() { return new LsAction(); }
    }

    /**
     * Prints the path of the current file/directory/archive entry and evaluates to {@code true}.
     */
    public static
    class PrintElement implements ExpressionElement {
        @Override public Expression toExpression() { return new PrintAction(); }
    }

    /**
     * Prints a text and evaluates to {@code true}.
     * <p>
     *   All occurrences of "{@code @{variable-name}}" in the text are replaced with the value of the named variable.
     *   For the list of supported variables, see <a
     *   href="../../javadoc/de/unkrig/zz/find/Find.Expression.html#evaluate(de.unkrig.commons.lang.protocol.Mapping)">
     *here</a>.
     * </p>
     */
    public static
    class EchoElement implements ExpressionElement {

        @Nullable private String message;

        /**
         * The text to print.
         */
        public void
        setMessage(String text) { this.message = text; }

        @Override public Expression
        toExpression() {

            String message = this.message;
            if (message == null) throw new BuildException("Attribute 'message' must be set");

            return new EchoAction(message);
        }
    }

    /**
     * Copies the contents of the current file/archive entry to the STDIN of a command and returns whether the command
     * exited with status 0.
     */
    public static
    class PipeElement implements ExpressionElement {

        @Nullable private String command;

        /**
         * The command to execute; program name and command line arguments separated by whitespace.
         *
         * @ant.mandatory
         */
        public void
        setCommand(String command) { this.command = command; }

        @Override public Expression
        toExpression() {
            String command = this.command;
            if (command == null) throw new BuildException("Attribute 'command' must be set");
            return new PipeAction(Arrays.asList(command.split("\\s+")), null);
        }
    }

    /**
     * Sets an ANT property.
     */
    public static
    class PropertyElement extends ProjectComponent implements ExpressionElement {

        @Nullable private String propertyName;
        @Nullable private String propertyValue;

        /**
         * The name of the property to set.
         *
         * @ant.mandatory
         */
        public void
        setName(String propertyName) { this.propertyName = propertyName; }

        /**
         * The text to store in the property.
         */
        public void
        setValue(String text) { this.propertyValue = text; }

        @Override public Expression
        toExpression() {

            String propertyName = this.propertyName;
            if (propertyName == null) throw new BuildException("Attribute 'propertyName' must be set");

            String propertyValue = this.propertyValue;
            if (propertyValue == null) throw new BuildException("Attribute 'propertyValue' must be set");

            return new PropertyAction(this.getProject(), propertyName, propertyValue);
        }
    }

    /**
     * Sets a particular property.
     */
    static
    class PropertyAction implements Action {

        private final Project project;
        private final String  propertyName;
        private final String  propertyValue;

        PropertyAction(Project project, String propertyName, String propertyValue) {
            this.project       = project;
            this.propertyName  = propertyName;
            this.propertyValue = propertyValue;
        }

        @Override public boolean
        evaluate(Mapping<String, Object> properties) {
            String pn = Find.expandVariables(this.propertyName, properties);
            String pv = Find.expandVariables(this.propertyValue, properties);
            this.project.setProperty(pn, pv);
            return true;
        }

        @Override public String
        toString() { return "(set property \"" + this.propertyName + "\" to \"" + this.propertyValue + "\")"; }
    }

    /**
     * Combines subexpressions logically: Evaluates its subexpressions sequentially; if one of them evaluates to {@code
     * false}, then the remaining subexpressions are not evaluated, and {@code false} is returned.
     */
    public static final
    class AndElement extends AbstractElementWithOperands implements ExpressionElement {

        private Expression predicate = Test.TRUE;

        @Override public void
        addConfigured(ExpressionElement operand) {
            this.predicate = new AndTest(this.predicate, operand.toExpression());
        }

        @Override public Expression
        toExpression() { return this.predicate; }
    }

    /**
     * Combines subexpressions logically: Evaluates its subexpressions sequentially; if one of them evaluates to {@code
     * true}, then the remaining subexpressions are not evaluated, and {@code true} is returned.
     */
    public static final
    class OrElement extends AbstractElementWithOperands implements ExpressionElement {

        private Expression predicate = Test.FALSE;

        @Override public void
        addConfigured(ExpressionElement operand) {
            this.predicate = new OrTest(this.predicate, operand.toExpression());
        }

        @Override public Expression
        toExpression() { return this.predicate; }
    }

    /**
     * Evaluates subexpressions sequentially; returns the value of the last subexpression.
     */
    public static final
    class CommaElement extends AbstractElementWithOperands implements ExpressionElement {
        private Expression predicate = Test.FALSE;

        @Override public void
        addConfigured(ExpressionElement operand) {
            this.predicate = new CommaTest(this.predicate, operand.toExpression());
        }

        @Override public Expression
        toExpression() { return this.predicate; }
    }

    /**
     * Evaluates to {@code true} iff the name of the current file/directory/archive entry matches the given
     * <var>glob</var>.
     */
    public static final
    class NameElement implements ExpressionElement {
        @Nullable private String value;

        /**
         * The glob to compare against.
         *
         * @ant.mandatory
         */
        public void
        setValue(String glob) { this.value = glob; }

        @Override public Expression
        toExpression() {
            String value = this.value;
            if (value == null) throw new BuildException("Attribute 'value' must be set");
            return new NameTest(value);
        }
    }

    /**
     * Evaluates to {@code true} iff the path of the current file/directory/archive entry matches the given
     * <var>glob</var>.
     * <p>
     *   The underscore in the name is there to resolve the name collision with ANT's {@code <path>} type, which,
     *   because it is a valid {@linkplain #addConfigured(ResourceCollection) resource collection}, is also a valid
     *   subelement of {@link AntTask}.
     * </p>
     */
    public static final
    class PathElement implements ExpressionElement {
        @Nullable private String value;

        /**
         * The glob to compare against.
         *
         * @ant.mandatory
         */
        public void
        setValue(String glob) { this.value = glob; }

        @Override public Expression
        toExpression() {
            String value = this.value;
            if (value == null) throw new BuildException("Attribute 'value' must be set");
            return new PathTest(value);
        }
    }

    /**
     * Evaluates to whether the type of the current file/directory/archive entry matches.
     * <p>
     *   Actual types are:
     * </p>
     * <dl>
     *   <dt>{@code directory}</dt>
     *   <dd>A directory</dd>
     *   <dt>{@code file}</dt>
     *   <dd>A (non-archive, not-compressed) file</dd>
     *   <dt>{@code archive-file}</dt>
     *   <dt>{@code archive-xxx-resource} (e.g. xxx="http")</dt>
     *   <dd>An archive file</dd>
     *   <dt>{@code compressed-file}</dt>
     *   <dt>{@code compressed-xxx-resource} (e.g. xxx="http")</dt>
     *   <dd>A compressed file</dd>
     *   <dt>{@code archive}</dt>
     *   <dd>A nested archive</dd>
     *   <dt>{@code normal-contents}</dt>
     *   <dd>Normal (non-archive, not-compressed) content</dd>
     *   <dt>{@code directory-entry}</dt>
     *   <dd>A 'directory entry' in an archive.</dd>
     * </dl>
     */
    public static final
    class TypeElement implements ExpressionElement {

        @Nullable private String value;

        /**
         * The glob to compare the type against.
         */
        public void
        setValue(String glob) { this.value = glob; }

        @Override public Expression
        toExpression() {
            String value = this.value;
            if (value == null) throw new BuildException("Attribute 'value' must be set");
            return new TypeTest(value);
        }
    }

    /**
     * Negates its subelement expression.
     */
    public static
    class NotElement extends AbstractElementWithOperands implements ExpressionElement {
        @Nullable private Expression operand;

        /**
         * The expression to negate.
         *
         * @ant.mandatory
         */
        @Override public void
        addConfigured(ExpressionElement operand) {
            if (this.operand != null) throw new IllegalArgumentException("No more than one subelement allowed");
            this.operand = operand.toExpression();
        }

        @Override public Expression
        toExpression() {
            Expression operand = this.operand;
            if (operand == null) throw new BuildException("One 'expressionElement' subelement must exist");
            return new NotExpression(operand);
        }
    }

    /**
     * Evaluates to {@code true} iff the current file/directory is readable.
     */
    public static
    class ReadableElement implements ExpressionElement {

        @Override public Expression
        toExpression() { return new ReadabilityTest(); }
    }

    /**
     * Evaluates to {@code true} iff the current file/directory is writable.
     */
    public static
    class WritableElement implements ExpressionElement {

        @Override public Expression
        toExpression() { return new WritabilityTest(); }
    }

    /**
     * Evaluates to {@code true} iff the current file/directory is executable.
     */
    public static
    class ExecutableElement implements ExpressionElement {

        @Override public Expression
        toExpression() { return new ExecutabilityTest(); }
    }

    /**
     * Checks the size of the current file or archive entry.
     */
    public static
    class SizeElement implements ExpressionElement {

        @Nullable private Predicate<? super Long> predicate;

        /**
         * Specifies that the size of the current file is exactly (greater than, less than) <var>N</var>.
         * <var>N</var> is an integer, optionally followed by "{@code k}" (* 1000), "{@code M}" (* 1000000) or "{@code
         * G}" (* 1000000000).
         *
         * @ant.valueExplanation N|+N|-N
         * @ant.mandatory
         */
        public void
        setValue(String value) { this.predicate = Parser.parseNumericArgument(value); }

        @Override public Expression
        toExpression() {
            Predicate<? super Long> p = this.predicate;
            if (p == null) throw new IllegalArgumentException("\"value\" attribute missing");
            return new SizeTest(p);
        }
    }

    /**
     * Checks the modification time of the current file or archive entry.
     */
    public static
    class ModificationTimeElement implements ExpressionElement {

        @Nullable private Predicate<? super Long> predicate;
        private long                              factor;

        /**
         * @deprecated Use {@link #setDays(String)} instead
         */
        @Deprecated
        public void
        setValue(String value) { this.setDays(value); }

        /**
         * Specifies that the file or archive entry was last modified exactly (more than, less than) <var>N</var>
         * days ago.
         * "<var>N</var> days ago" means "between <var>N</var>*24h and <var>N</var>*24h+23:59:59.999h ago".
         *
         * @ant.valueExplanation N|+N|-N
         */
        public void
        setDays(String value) {
            if (this.predicate != null) {
                throw new BuildException("\"days=...\" and \"-minutes=...\" are mutually exclusive");
            }
            this.predicate = Parser.parseNumericArgument(value);
            this.factor    = ModificationTimeTest.DAYS;
        }

        /**
         * Specifies that the file or archive entry was last modified exactly (more than, less than) <var>N</var>
         * minutes ago.
         * "<var>N</var> minutes ago" means "between <var>N</var> minutes and <var>N</var> minutes plus 59.999 seconds
         * ago".
         *
         * @ant.valueExplanation N|+N|-N
         */
        public void
        setMinutes(String value) {
            if (this.predicate != null) {
                throw new BuildException("\"days=...\" and \"-minutes=...\" are mutually exclusive");
            }
            this.predicate = Parser.parseNumericArgument(value);
            this.factor    = ModificationTimeTest.MINUTES;
        }

        @Override public Expression
        toExpression() {
            Predicate<? super Long> p = this.predicate;
            if (p == null) {
                throw new IllegalArgumentException(
                    "Exactly one of \"days=...\" and \"-minutes=...\" must be configured"
                );
            }
            return new ModificationTimeTest(p, this.factor);
        }
    }

    /**
     * Evaluates to {@code true}.
     */
    public static
    class TrueElement implements ExpressionElement {
        @Override public Expression toExpression() { return Test.TRUE; }
    }

    /**
     * Evaluates to {@code false}.
     */
    public static
    class FalseElement implements ExpressionElement {
        @Override public Expression toExpression() { return Test.FALSE; }
    }

    /**
     * Evaluates to {@code true}. If the file is a directory, do not descend into it.
     */
    public static
    class PruneElement implements ExpressionElement {
        @Override public Expression toExpression() { return new PruneAction(); }
    }

    /**
     * Delete file; Evaluates to {@code true} if removal succeeded. If the removal failed, an error message is issued.
     * If you want to delete <em>directories</em>, also configure the {@code --descendants-first} option, for
     * otherwise the directory is first deleted, and then traversed, which cannot possibly work.
     */
    public static
    class DeleteElement implements ExpressionElement {
        @Override public Expression toExpression() { return new DeleteAction(); }
    }

    // BEGIN CONFIGURATION SETTERS

    /**
     * Archive files, nested archives, compressed files and nested compressed content are introspected iff the
     * archive/compression format and the file's (resp. nested archive entry's) path match the given globs.
     * <p>
     *   The default is to look into <i>any</i> recognized archive and comressed content.
     * </p>
     *
     * @ant.valueExplanation <var>format-glob</var>:<var>path-glob</var>
     */
    public void
    setLookInto(String value) {
        this.find.setLookIntoFormat(Glob.compile(value, Pattern2.WILDCARD | Glob.INCLUDES_EXCLUDES));
    }

    /**
     * Whether to process each directory's contents before the directory itself, and each archive's entries before
     * the archive itself, and each compressed contents before the enclosing file or archive entry.
     */
    public void
    setDescendantsFirst(boolean value) { this.find.setDescendantsFirst(value); }

    /**
     * Do not apply any tests or actions at levels less than <var>levels</var>. For example, "1" means "process all
     * files except the top level files".
     *
     * @ant.defaultValue 0
     */
    public void
    setMinDepth(int levels) { this.find.setMinDepth(levels); }

    /**
     * Descend at most <var>levels</var> of directories below the top level files and directories. For example, "0"
     * means "only apply the tests and actions to the top level files and directories". The default is to descend to
     * <em>any</em> nesting level.
     */
    public void
    setMaxDepth(int levels) { this.find.setMaxDepth(levels); }

    /**
     * Also examine the given <var>file</var>.
     *
     * @see #setDir(File)
     * @see #addConfigured(ResourceCollection)
     */
    public void
    setFile(File file) { this.resourceCollections.add(new FileResource(file)); }

    /**
     * Also examine the given <var>directory</var>.
     *
     * @see #setFile(File)
     * @see #addConfigured(ResourceCollection)
     */
    public void
    setDir(File directory) { this.resourceCollections.add(new FileResource(directory)); }

    /**
     * Print the output to the given file instead of STDOUT.
     */
    public void
    setOutputFile(File file) { this.outputFile = file; }

    /**
     * Another expression that will be evaluated for each directory, file and archive entry.
     */
    @Override public void
    addConfigured(ExpressionElement operand) { this.root.addConfigured(operand); }

    /**
     * A {@code <sequential>} subelement creates a custom {@link Expression} which is only available in the ANT
     * binding: A sequence of ANT tasks that evaluates to {@code true}.
     */
    static NestedSequential
    newSequential(final AbstractElementWithOperands container) {
        final MacroDef macroDef = new MacroDef();

        macroDef.setProject(container.getProject());

        {
            MacroDef.Attribute attribute = new MacroDef.Attribute();
            attribute.setName("name");
            macroDef.addConfiguredAttribute(attribute);
        }
        {
            MacroDef.Attribute attribute = new MacroDef.Attribute();
            attribute.setName("path");
            macroDef.addConfiguredAttribute(attribute);
        }
        {
            MacroDef.Attribute attribute = new MacroDef.Attribute();
            attribute.setName("entryName");
            macroDef.addConfiguredAttribute(attribute);
        }

        ExpressionElement operand = new ExpressionElement() {

            @Override public Expression
            toExpression() {
                return new Expression() {

                    @Override public boolean
                    evaluate(Mapping<String, Object> properties) {
                        MacroInstance instance = new MacroInstance();
                        instance.setProject(container.getProject());
//                        instance.setOwningTarget(xxx.getOwningTarget());
                        instance.setMacroDef(macroDef);

                        for (String attributeName : new String[] { "name", "path" }) {
                            Object attributeValue = properties.get(attributeName);
                            if (attributeValue != null) {
                                instance.setDynamicAttribute(attributeName, attributeValue.toString());
                            }
                        }

                        instance.execute();

                        return true;
                    }

                    @Override public String
                    toString() { return "<sequential " + macroDef.getLocation() + ">"; }
                };
            }
        };
        container.addConfigured(operand);

        return macroDef.createSequential();
    }

    /**
     * Resources to examine.
     *
     * @see #setFile(File)
     * @see #setDir(File)
     */
    public void
    addConfigured(ResourceCollection value) { this.resourceCollections.add(value); }

    // END CONFIGURATION SETTERS

    /**
     * The ANT task "execute" method.
     *
     * @see Task#execute
     */
    public void
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
    execute2() {

        this.find.setExpression(this.root.toExpression());

        final boolean[] hadExceptions = new boolean[1];
        ConsumerWhichThrows<IOException, IOException>
        exceptionHandler = new ConsumerWhichThrows<IOException, IOException>() {

            @Override public void
            consume(IOException ioe) {
                AntTask.this.getProject().log(null, ioe, Project.MSG_ERR);
                hadExceptions[0] = true;
            }
        };
        this.find.setExceptionHandler(exceptionHandler);

        for (ResourceCollection rc : this.resourceCollections) {

            for (@SuppressWarnings("unchecked") Iterator<Resource> it = rc.iterator(); it.hasNext();) {
                Resource resource = it.next();

                try {
                    this.execute3(resource);
                } catch (IOException ioe) {
                    AntTask.this.getProject().log(null, ioe, Project.MSG_ERR);
                    hadExceptions[0] = true;
                }
            }
        }

        if (hadExceptions[0]) {
            throw new BuildException("One or more files had i/o exceptions");
        }
    }

    private void
    execute3(final Resource resource) throws IOException {

        AntTask.execute4(
            new RunnableWhichThrows<IOException>() {

                @Override public void
                run() throws IOException {

                    if (resource instanceof FileProvider) {

                        AntTask.this.find.findInFile(((FileProvider) resource).getFile());
                    } else {

                        InputStream is = resource.getInputStream();
                        try {

                            AntTask.this.find.findInStream(is);
                            is.close();
                        } catch (IOException ioe) {
                            try { is.close(); } catch (Exception e) {}
                        }
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
    execute4(RunnableWhichThrows<IOException> runnable, @Nullable File outputFile, final ProjectComponent component)
    throws IOException {

        AbstractPrinter printer = new AbstractPrinter() {
            @Override public void warn(@Nullable String message)    { component.log(message, Project.MSG_WARN);    }
            @Override public void verbose(@Nullable String message) { component.log(message, Project.MSG_VERBOSE); }
            @Override public void info(@Nullable String message)    { component.log(message, Project.MSG_INFO);    }
            @Override public void error(@Nullable String message)   { component.log(message, Project.MSG_ERR);     }
            @Override public void debug(@Nullable String message)   { component.log(message, Project.MSG_DEBUG);   }
        };

        if (outputFile == null) {
            printer.run(runnable);
        } else {
            final Writer out = new FileWriter(outputFile);
            try {

                printer.redirectInfo(out).run(runnable);
                out.close();
            } finally {
                try { out.close(); } catch (Exception e) {}
            }
        }
    }

    /**
     * Iff the <var>propertyName</var> is not {@code null}, then the <var>action</var> is wrapped such that
     * when it is evaluated, its INFO output is stored in the named property.
     */
    private static Action
    redirectInfoToProperty(
        final Project          project,
        final Action           action,
        @Nullable final String propertyName
    ) {

        if (propertyName  == null) return action;

        return new Action() {

            @Override public boolean
            evaluate(final Mapping<String, Object> properties) {

                final boolean[] result = new boolean[1];
                AbstractPrinter.getContextPrinter().redirectInfo(new Consumer<String>() {
                    @Override public void consume(String subject) { project.setProperty(propertyName, subject); }
                }).run(new Runnable() {
                    @Override public void run() { result[0] = action.evaluate(properties); }
                });
                return result[0];
            }
        };
    }
}
