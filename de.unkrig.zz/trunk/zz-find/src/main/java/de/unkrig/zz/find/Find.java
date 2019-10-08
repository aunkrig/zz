
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Formatter;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.Checksum;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.zip.UnsupportedZipFeatureException;
import org.apache.commons.compress.compressors.CompressorInputStream;

import de.unkrig.commons.file.CompressUtil;
import de.unkrig.commons.file.CompressUtil.ArchiveHandler;
import de.unkrig.commons.file.CompressUtil.CompressorHandler;
import de.unkrig.commons.file.CompressUtil.NormalContentsHandler;
import de.unkrig.commons.file.FileUtil;
import de.unkrig.commons.file.org.apache.commons.compress.archivers.ArchiveFormat;
import de.unkrig.commons.file.org.apache.commons.compress.archivers.ArchiveFormatFactory;
import de.unkrig.commons.file.org.apache.commons.compress.compressors.CompressionFormat;
import de.unkrig.commons.file.org.apache.commons.compress.compressors.CompressionFormatFactory;
import de.unkrig.commons.file.resourceprocessing.ResourceProcessings;
import de.unkrig.commons.io.InputStreams;
import de.unkrig.commons.io.IoUtil;
import de.unkrig.commons.lang.AssertionUtil;
import de.unkrig.commons.lang.ExceptionUtil;
import de.unkrig.commons.lang.ProcessUtil;
import de.unkrig.commons.lang.protocol.ConsumerUtil;
import de.unkrig.commons.lang.protocol.ConsumerWhichThrows;
import de.unkrig.commons.lang.protocol.Mapping;
import de.unkrig.commons.lang.protocol.Mappings;
import de.unkrig.commons.lang.protocol.Predicate;
import de.unkrig.commons.lang.protocol.PredicateUtil;
import de.unkrig.commons.lang.protocol.Producer;
import de.unkrig.commons.lang.protocol.RunnableUtil;
import de.unkrig.commons.lang.protocol.RunnableWhichThrows;
import de.unkrig.commons.nullanalysis.Nullable;
import de.unkrig.commons.text.Printers;
import de.unkrig.commons.text.expression.EvaluationException;
import de.unkrig.commons.text.expression.ExpressionEvaluator;
import de.unkrig.commons.text.parser.ParseException;
import de.unkrig.commons.text.pattern.Glob;
import de.unkrig.commons.text.pattern.Pattern2;
import de.unkrig.jdisasm.Disassembler;

/**
 * The central API for the ZZFIND functionality.
 */
public
class Find {

    private static final String PRUNE_PROPERTY_NAME = "$PRUNE";

    static { AssertionUtil.enableAssertionsForThisClass(); }

    private static final Logger LOGGER = Logger.getLogger(Find.class.getName());

    // BEGIN CONFIGURATION VARIABLES

    private Predicate<? super String> lookIntoFormat = PredicateUtil.always();
    private boolean                   descendantsFirst;
    private int                       minDepth;
    private int                       maxDepth = Integer.MAX_VALUE;

    /**
     * The expression to match files/entries against.
     */
    private Expression expression = Test.TRUE;

    private ConsumerWhichThrows<? super IOException, IOException> exceptionHandler = ConsumerUtil.throwsSubject();

    // END CONFIGURATION VARIABLES

    // BEGIN CONFIGURATION SETTERS

    /**
     * @param value Is evaluated against <code>"<i>format</i>:<i>path</i>"</code>
     * @see         ArchiveFormatFactory#allFormats()
     * @see         ArchiveFormat#getName()
     * @see         CompressionFormatFactory#allFormats()
     * @see         CompressionFormat#getName()
     */
    public void
    setLookIntoFormat(Predicate<? super String> value) {
        Find.LOGGER.log(Level.FINE, "setLookIntoFormat({0})", value);

        this.lookIntoFormat = value;
    }

    /**
     * Whether to process each directory's contents before the directory itself, and each archive's entries before
     * the archive itself, and each compressed contents before the enclosing file or archive entry.
     */
    public void
    setDescendantsFirst(boolean value) { this.descendantsFirst = value; }

    /**
     * Do not apply any tests or actions at levels less than <var>levels</var>. E.g. "1" means "process all files
     * except the top level files".
     */
    public void
    setMinDepth(int levels) {

        // Negative values have exactly the same effect as zero and are thus not forbidden.

        this.minDepth = levels;
    }

    /**
     * Descend at most <var>levels</var> of directories below the top level files and directories. "0" means "only
     * apply the tests and actions to the top level files and directories".
     */
    public void
    setMaxDepth(int levels) {

        // Negative values cause "find()" to return immediately. This is only logical, and thus negative values are not
        // forbidden.

        this.maxDepth = levels;
    }

    /**
     * Sets the "find expression", i.e. the construct specified with the "-name", "-print", etc. command line options.
     */
    public void
    setExpression(Expression value) {
        Find.LOGGER.log(Level.FINE, "setExpression({0})", value);

        this.expression = value;
    }

    /**
     * Under some conditions recovery from exceptions within {@link #findInResource(String, URL)} and {@link
     * #findInStream(InputStream)} makes sense, e.g. by continuing with the "next file". For this purpose a custom
     * exception handler can be configured.
     * <p>
     *   The default behavior is to not attempt exception recovery, i.e. {@link #findInResource(String, URL)} resp.
     *   {@link #findInStream(InputStream)} complete abnormally on the first {@link IOException} that occurs.
     * </p>
     */
    public Find
    setExceptionHandler(ConsumerWhichThrows<? super IOException, IOException> value) {
        this.exceptionHandler = value;
        return this;
    }

    /** Getter for the ZZFIND expression. */
    public Expression
    getExpression() { return this.expression; }

    // END CONFIGURATION SETTERS

    /**
     * Representation of the "FIND expression".
     */
    public
    interface Expression extends Predicate<Mapping<String, Object>> {

        // SUPPRESS CHECKSTYLE LineLength:26
        /**
         * Evaluates to {@code true} or {@code false}, depending on the <var>properties</var>:
         * <dl>
         *   <dt>String name</dt>
         *   <dd>
         *     The name of the file, directory or archive entry. For archive entries, the name is relative to the
         *     archive, i.e. it may contain slashes ("{@code /}").
         *   </dd>
         *   <dt>String path</dt>
         *   <dd>
         *     The path of the file, directory or archive entry.
         *   </dd>
         *   <dt>String type</dt>
         *   <dd>
         *     The type of the file, directory or archive entry:
         *     <dl>
         *       <dt>"{@code directory}"</dt>                                <dd>A directory on the file system</dd>
         *       <dt>"{@code file}"</dt>                                     <dd>A file on the file system</dd>
         *       <dt>"{@code archive-file}"</dt>                             <dd>An archive file on the file system</dd>
         *       <dt>"{@code archive-xxx-resource}" (e.g. xxx="http")</dt>   <dd>A resource designated by a URL</dd>
         *       <dt>"{@code compressed-file}"</dt>                          <dd>A compressed file on the file system</dd>
         *       <dt>"{@code compressed-xxx-resource}" (e.g. xxx="http")</dt><dd>A compressed resource</dd>
         *       <dt>"{@code directory-entry}"</dt>                          <dd>An archive entry which denotes a directory</dd>
         *       <dt>"{@code archive}"</dt>                                  <dd>An archive nested inside an archive or compressed file</dd>
         *       <dt>"{@code compressed-contents}"</dt>                      <dd>Nested compressed contents</dd>
         *       <dt>"{@code normal-contents}"</dt>                          <dd>(Non-compressed, non-archive) contents in an archive or in a compressed file</dd>
         *     </dl>
         *   </dd>
         * </dl>
         * <p>
         *   The following properties apply to the current file or directory, or, iff the current
         *   document is the contents of a compressed file, or the contents of an archive entry, to the enclosing
         *   compressed resp. archive file:
         * </p>
         * <dl>
         *   <dt>String absolutePath</dt>
         *   <dd>
         *     The absolute path of the file or directory; see {@link File#getAbsolutePath()}.
         *   </dd>
         *   <dt>String canonicalPath</dt>
         *   <dd>
         *     The "canonical path" of the file or directory; see {@link File#getCanonicalPath()}.
         *   </dd>
         *   <dt>Date lastModifiedDate</dt>
         *   <dd>
         *     The "modification time" of the file or directory.
         *     Notice that the comparison operators (e.g. "{@code ==}") silently convert dates into strings with
         *     format "{@code EEE MMM dd HH:mm:ss zzz yyyy}" (see {@link SimpleDateFormat}).
         *   </dd>
         *   <dt>long size</dt>
         *   <dd>
         *     The size of the file (zero for directories).
         *   </dd>
         *   <dt>long freeSpace</dt>
         *   <dd>
         *     The number of unallocated bytes in the partition (see {@link File#getFreeSpace()}.
         *   </dd>
         *   <dt>long totalSpace</dt>
         *   <dd>
         *     The size of the partition (see {@link File#getTotalSpace()}.
         *   </dd>
         *   <dt>long usableSpace</dt>
         *   <dd>
         *     The number of bytes available to this virtual machine on the partition (see {@link
         *     File#getUsableSpace()}.
         *   </dd>
         *   <dt>boolean isDirectory</dt>
         *   <dd>
         *     {@code true} for a directory, otherwise {@code false}.
         *   </dd>
         *   <dt>boolean isFile</dt>
         *   <dd>
         *     {@code false} for a directory, otherwise {@code true}.
         *   </dd>
         *   <dt>boolean isHidden</dt>
         *   <dd>
         *     Whether the file or directory is a hidden file (see {@link File#isHidden()}).
         *   </dd>
         *   <dt>boolean isReadable</dt>
         *   <dd>
         *     Whether the application can read the file or directory (see {@link File#canRead()}.
         *   </dd>
         *   <dt>boolean isWritable</dt>
         *   <dd>
         *     Whether the application can modify the file or directory (see {@link File#canWrite()}.
         *   <dd>
         *   </dd>
         *   <dt>boolean isExecutable</dt>
         *   <dd>
         *     Whether the application can execute the file (see {@link File#canExecute()}).
         *   </dd>
         * </dl>
         * <p>
         *   The following properties are available iff the current file is an archive file, or the current document
         *   exists within the contents of an archive:
         * </p>
         * <dl>
         *   <dt>String archiveFormat</dt>
         *   <dd>
         *     The format of the immediately enclosing archive.
         *   </dd>
         * </dl>
         * <p>
         *   The following properties are available iff the current file is a compressed file, or the current
         *   document exists within compressed contents:
         * </p>
         * <dl>
         *   <dt>String compressionFormat</dt>
         *   <dd>
         *     The format of the immediately enclosing compressed document.
         *   </dd>
         * </dl>
         */
        @Override boolean evaluate(Mapping<String, Object> properties);
    }

    /**
     * An {@link Expression} that has no side effects.
     */
    interface Test extends Expression {

        @Override boolean evaluate(Mapping<String, Object> properties);

        /** A {@link Find.Test} which unconditionally evaluates to {@code true}. */
        Test TRUE = new ConstantTest(true);

        /** A {@link Find.Test} which unconditionally evaluates to {@code false}. */
        Test FALSE = new ConstantTest(false);
    }

    /**
     * Evaluates to a constant boolean value.
     */
    public static
    class ConstantTest implements Test {

        private final boolean value;

        public
        ConstantTest(boolean value) { this.value = value; }

        @Override public boolean
        evaluate(Mapping<String, Object> properties) { return this.value; }

        @Override public String
        toString() { return String.valueOf(this.value); }
    }

    /**
     * A {@link Find.Test} with one operand expression.
     */
    public abstract static
    class UnaryTest implements Test {

        /** The single operand of this test. */
        protected final Expression operand;

        UnaryTest(Expression operand) { this.operand = operand; }
    }

    /**
     * A {@link Find.Test} with two operand expressions.
     */
    public abstract static
    class BinaryTest implements Test {

        /** The two operands of this test. */
        protected final Expression lhs, rhs;

        BinaryTest(Expression lhs, Expression rhs) { this.lhs = lhs; this.rhs = rhs; }
    }

    /**
     * Evaluates {@code lhs}, then {@code rhs}, and returns the result of the latter evaluation.
     */
    public static
    class CommaTest extends BinaryTest {

        CommaTest(Expression lhs, Expression rhs) { super(lhs, rhs); }

        @Override public boolean
        evaluate(Mapping<String, Object> properties) {
            this.lhs.evaluate(properties);
            return this.rhs.evaluate(properties);
        }

        @Override public String
        toString() { return "(" + this.lhs + ", " + this.rhs + ")"; }
    }

    /**
     * Iff {@code lhs} evaluates to FALSE, then {@code rhs} is evaluated and its result is returned. Otherwise, TRUE
     * is returned.
     */
    public static
    class OrTest extends BinaryTest {

        OrTest(Expression lhs, Expression rhs) { super(lhs, rhs); }

        @Override public boolean
        evaluate(Mapping<String, Object> properties) {
            return this.lhs.evaluate(properties) || this.rhs.evaluate(properties);
        }

        @Override public String
        toString() { return "(" + this.lhs + " || " + this.rhs + ")"; }
    }

    /**
     * Iff {@code lhs} evaluates to TRUE, then {@code rhs} is evaluated and its result is returned. Otherwise, FALSE
     * is returned.
     */
    public static
    class AndTest extends BinaryTest {

        AndTest(Expression lhs, Expression rhs) { super(lhs, rhs); }

        @Override public boolean
        evaluate(Mapping<String, Object> properties) {
            return this.lhs.evaluate(properties) && this.rhs.evaluate(properties);
        }

        @Override public String
        toString() { return "(" + this.lhs + " && " + this.rhs + ")"; }
    }

    /**
     * Evaluates a delegate expression and negates its result.
     */
    public static
    class NotExpression extends UnaryTest {

        NotExpression(Expression operand) { super(operand); }

        @Override public boolean
        evaluate(Mapping<String, Object> properties) { return !this.operand.evaluate(properties); }

        @Override public String
        toString() { return "(not " + this.operand + ")"; }
    }

    /**
     * Gets and returns the value of a boolean property, or {@code false} if that property is not set.
     */
    public static
    class BooleanTest implements Test {

        private final String propertyName;

        /** @see #evaluate(Mapping) */
        public BooleanTest(String propertyName) { this.propertyName = propertyName; }

        /**
         * @return The value of the named boolean property of the {@code subject}
         */
        @Override public boolean
        evaluate(Mapping<String, Object> properties) {
            Boolean value = Mappings.get(properties, this.propertyName, Boolean.class);
            return value != null && value.booleanValue();
        }

        @Override public final String
        toString() { return this.propertyName; }
    }

    /**
     * Evaluates a predicate for a property's value and returns the result, or {@code null} iff the property is not
     * set.
     *
     * @param <T> The type of the property and the predicate
     */
    public abstract static
    class PredicateTest<T> implements Test {

        private final Predicate<? super T> predicate;
        private final Class<T>             propertyType;
        private final String               propertyName;

        PredicateTest(String propertyName, Class<T> propertyType, Predicate<? super T> predicate) {
            this.propertyName = propertyName;
            this.propertyType = propertyType;
            this.predicate    = predicate;
        }

        @Override public final boolean
        evaluate(Mapping<String, Object> properties) {
            T propertyValue = Mappings.get(properties, this.propertyName, this.propertyType);
            return propertyValue != null && this.predicate.evaluate(propertyValue);
        }

        @Override public final String
        toString() { return "( " + this.propertyName + " =* '" + this.predicate + "')"; }
    }

    /**
     * Evaluates a property's value, converted to {@link String}, against a predicate.
     */
    public static
    class StringPredicateTest implements Test {

        private final Predicate<? super String> predicate;
        private final String                    propertyName;

        StringPredicateTest(String propertyName, Predicate<? super String> predicate) {
            this.propertyName = propertyName;
            this.predicate    = predicate;
        }

        @Override public boolean
        evaluate(Mapping<String, Object> properties) {
            Object propertyValue = Mappings.get(properties, this.propertyName, Object.class);
            return propertyValue != null && this.predicate.evaluate(propertyValue.toString());
        }

        @Override public final String
        toString() { return "( " + this.propertyName + " =* '" + this.predicate + "')"; }
    }

    /**
     * Matches a {@link Glob} with a property value.
     */
    public static
    class GlobTest extends StringPredicateTest {

        GlobTest(String propertyName, String pattern) {
            super(propertyName, Glob.compile(pattern, Pattern2.WILDCARD | Glob.INCLUDES_EXCLUDES));
        }
    }

    /**
     * Tests the value of property "name" against a {@link Glob}.
     */
    public static
    class NameTest extends GlobTest {
        public NameTest(String nameGlob) { super("name", nameGlob); }
    }

    /**
     * Tests the value of property "path" against a {@link Glob}.
     */
    public static
    class PathTest extends GlobTest {
        public PathTest(String pathGlob) { super("path", pathGlob); }
    }

    /**
     * Tests the value of property "type" against a {@link Glob}.
     */
    public static
    class TypeTest extends GlobTest {
        public TypeTest(String typeGlob) { super("type", typeGlob); }
    }

    /**
     * Tests the value of the boolean property "canRead".
     */
    public static
    class ReadabilityTest extends BooleanTest {
        public ReadabilityTest() { super("canRead"); }
    }

    /**
     * Tests the value of the boolean property "canWrite".
     */
    public static
    class WritabilityTest extends BooleanTest {
        public WritabilityTest() { super("canWrite"); }
    }

    /**
     * Tests the value of the boolean property "canExecute".
     */
    public static
    class ExecutabilityTest extends BooleanTest {
        public ExecutabilityTest() { super("canExecute"); }
    }

    /**
     * Tests the value of the LONG property "size".
     */
    public static
    class SizeTest extends PredicateTest<Long> {

        public
        SizeTest(Predicate<? super Long> predicate) { super("size", Long.class, predicate); }
    }

    /**
     * Representation of a {@link Find.Test} which checks the node's modification time against the current time and a
     * (days-based) predicate.
     *
     * <dl>
     *   <dt>==0<dd>0...23:59:59.000
     *   <dt>==1<dd>24...47:59:59.000
     *   <dt>==2<dd>48...71:59:59.000
     * </dl>
     *
     * <dl>
     *   <dt>&gt;0<dd>>= 24h
     *   <dt>&gt;1<dd>>= 48h
     *   <dt>&gt;2<dd>>= 72h
     * </dl>
     *
     * <dl>
     *   <dt>&lt;1<dd>&lt; 24h
     *   <dt>&lt;2<dd>&lt; 48h
     *   <dt>-3<dd>&lt; 72h
     * </dl>
     */
    public static
    class ModificationTimeTest extends PredicateTest<Date> {

        /**
         * @see ModificationTimeTest
         */
        public static final long DAYS = 24L * 3600L * 1000L;

        /**
         * @see ModificationTimeTest
         */
        public static final long MINUTES = 60L * 1000L;

        /**
         * Iff <var>factor</var> is {@link #DAYS}:
         * <dl>
         *   <dt>Age 0:00:00.000 ... 23:59:59.999:</dt><dd>Value 0</dd>
         *   <dt>Age 24:00:00.000 ... 47:59:59.999:</dt><dd>Value 1</dd>
         *   <dt>Age 48:00:00.000 ... 71:59:59.999:</dt><dd>Value 2</dd>
         *   <dd>etc.</dd>
         * </dl>
         * <p>
         *   Iff <var>factor</var> is {@link #MINUTES}:
         * </p>
         * <dl>
         *   <dt>Age 0:00:00.000 ... 0:00:59.999:</dt><dd>Value 0</dd>
         *   <dt>Age 0:01:00.000 ... 00:01:59.999:</dt><dd>Value 1</dd>
         *   <dt>Age 0:02:00.000 ... 00:02:59.999:</dt><dd>Value 2</dd>
         *   <dd>etc.</dd>
         * </dl>
         */
        public
        ModificationTimeTest(final Predicate<? super Long> predicate, final long factor) {
            super("lastModifiedDate", Date.class, new Predicate<Date>() {

                @Override public boolean
                evaluate(Date lastModifiedDate) {
                    long milliseconds = System.currentTimeMillis() - lastModifiedDate.getTime();
                    long days         = milliseconds / factor;
                    return predicate.evaluate(days);
                }

                @Override public String
                toString() { return "(" + predicate + " days)"; }
            });
        }
    }

    /**
     * An {@link Expression} that has side effects, e.g. text being printed.
     */
    interface Action extends Expression {
    }

    /**
     * Prints the path of the current file and returns {@code true}.
     */
    public static
    class PrintAction implements Action {

        @Override public boolean
        evaluate(Mapping<String, Object> properties) {
            Printers.info(Mappings.getNonNull(properties, "path", String.class));
            return true;
        }

        @Override public String
        toString() { return "(print)"; }
    }

    /**
     * Prints one message and returns {@code true}.
     */
    public static
    class EchoAction implements Action {

        private final de.unkrig.commons.text.expression.Expression message;

        EchoAction(String message) { this.message = Find.parseExt(message); }

        @Override public boolean
        evaluate(Mapping<String, Object> properties) {
            Printers.info(Find.evaluateExpression(this.message, properties));
            return true;
        }

        @Override public String
        toString() { return "(echo '" + this.message + "')"; }
    }

    /**
     * Prints one message and returns {@code true}.
     */
    public static
    class PrintfAction implements Action {

        private final String   format;
        private final String[] argExpressions;

        PrintfAction(String format, String[] argExpressions) {
            this.format         = format;
            this.argExpressions = argExpressions;
        }

        @Override public boolean
        evaluate(Mapping<String, Object> properties) {

            Object[] args = new Object[this.argExpressions.length];
            for (int i = 0; i < args.length; i++) {
                String argExpression = this.argExpressions[i];
                try {
                    args[i] = (
                        new ExpressionEvaluator(Mappings.containsKeyPredicate(properties))
                        .evaluate(argExpression, properties)
                    );
                } catch (ParseException pe) {
                    throw ExceptionUtil.wrap("Parsing '" + argExpression + "'", pe, RuntimeException.class);
                } catch (EvaluationException ee) {
                    throw ExceptionUtil.wrap("Evaluating '" + argExpression + "'", ee, RuntimeException.class);
                }
            }

            String message;
            try {
                message = new Formatter().format(this.format, args).out().toString();
            } catch (RuntimeException re) {
                throw ExceptionUtil.wrap("Formatting '" + this.format + "'", re);
            }

            Printers.info(message);

            return true;
        }

        @Override public String
        toString() { return "(echo '" + this.format + "')"; }
    }

    /**
     * Prints the file type ('d' or '-'), readability ('r' or '-'), writability ('w' or '-'), size, modification time
     * and path to the given {@link Writer} and evaluates to {@code true}.
     */
    public static
    class LsAction implements Action {

        @Override public boolean
        evaluate(Mapping<String, Object> properties) {

            Printers.info(String.format(
                "%c%c%c%c %10d %tF %<tT %s",
                Mappings.getNonNull(properties, "isDirectory",      boolean.class) ? 'd' : '-',
                Mappings.getNonNull(properties, "isReadable",       boolean.class) ? 'r' : '-',
                Mappings.getNonNull(properties, "isWritable",       boolean.class) ? 'w' : '-',
                Mappings.getNonNull(properties, "isExecutable",     boolean.class) ? 'x' : '-',
                Mappings.getNonNull(properties, "size",             Long.class),
                Mappings.getNonNull(properties, "lastModifiedDate", Date.class),
                Mappings.getNonNull(properties, "path",             String.class)
            ));

            return true;
        }

        @Override public String
        toString() { return "(lsp)"; }
    }

    /**
     * Executes an external command; the special string "<code>{}</code>" within the command is replaced with the full
     * path of the current file, directory or archive entry.
     */
    public static
    class ExecAction implements Action {

        private final List<de.unkrig.commons.text.expression.Expression>
        command = new ArrayList<de.unkrig.commons.text.expression.Expression>();

        ExecAction(List<String> command) {
            for (String word : command) this.command.add(Find.parseExt(word));
        }

        @Override public boolean
        evaluate(Mapping<String, Object> properties) {

            List<String> command2;
            {
                command2 = new ArrayList<String>();
                for (de.unkrig.commons.text.expression.Expression e : this.command) {
                    command2.add(Find.evaluateExpression(e, properties));
                }
            }

            try {
                return ProcessUtil.execute(
                    command2,   // command
                    null,       // workingDirectory
                    System.in,  // stdin
                    false,      // closeStdin
                    System.out, // stdout
                    false,      // closeStdout
                    System.err, // stderr
                    false       // closeStderr
                );
            } catch (Exception e) {
                throw ExceptionUtil.wrap("Executing '" + command2 + "'", e, RuntimeException.class);
            }
        }

        @Override public String
        toString() { return "(exec '" + this.command + "')"; }
    }

    /**
     * Copies the contents of the current file or archive entry to a given {@link OutputStream} and evaluates to {@code
     * true}.
     */
    public static
    class CatAction implements Action {

        private final OutputStream out;

        CatAction(OutputStream out) { this.out = out; }

        @Override public boolean
        evaluate(Mapping<String, Object> properties) {
            try {
                InputStream is = Mappings.getNonNull(properties, "inputStream", InputStream.class);
                IoUtil.copy(is, this.out);
            } catch (IOException ioe) {
                throw ExceptionUtil.wrap("Running '-cat' on '" + properties + "'", ioe, RuntimeException.class);
            }
            return true;
        }

        @Override public String
        toString() { return "(cat " + this.out + ")"; }
    }

    /**
     * Copies the contents of the current file or archive entry to a given file and evaluates to {@code true}.
     */
    public static
    class CopyAction implements Action {

        private final de.unkrig.commons.text.expression.Expression tofile;
        private final boolean                                      mkdirs;

        CopyAction(File tofile, boolean mkdirs) {
            this.tofile = Find.parseExt(tofile.getPath());
            this.mkdirs = mkdirs;
        }

        @Override public boolean
        evaluate(Mapping<String, Object> properties) {

            File tofile = new File(Find.evaluateExpression(this.tofile, properties));
            assert tofile != null;

            try {

                if (this.mkdirs) IoUtil.createMissingParentDirectoriesFor(tofile);

                InputStream  in  = Mappings.getNonNull(properties, "inputStream", InputStream.class);
                OutputStream out = new FileOutputStream(tofile);
                try {
                    IoUtil.copy(in, out);
                    out.close();
                } finally {
                    try { out.close(); } catch (IOException e) {}
                }
            } catch (IOException ioe) {
                throw ExceptionUtil.wrap(
                    "Copying \"" + properties + "\" to \"" + tofile + "\"",
                    ioe,
                    RuntimeException.class
                );
            }
            return true;
        }

        @Override public String
        toString() { return "(copy to '" + this.tofile + "')"; }
    }

    /**
     * Copies the contents of the current file or archive entry to the STDIN of a given command and returns whether the
     * command exited with status 0.
     */
    public static
    class PipeAction implements Action {

        private final List<de.unkrig.commons.text.expression.Expression>
        command = new ArrayList<de.unkrig.commons.text.expression.Expression>();

        @Nullable private final File workingDirectory;

        PipeAction(List<String> command, @Nullable File workingDirectory) {
            for (String word : command) this.command.add(Find.parseExt(word));
            this.workingDirectory = workingDirectory;
        }

        @Override public boolean
        evaluate(Mapping<String, Object> properties) {

            final InputStream in = Mappings.getNonNull(properties, "inputStream", InputStream.class);

            List<String> command2 = new ArrayList<String>();
            for (de.unkrig.commons.text.expression.Expression word : this.command) {
                command2.add(Find.evaluateExpression(word, properties));
            }

            try {

                return ProcessUtil.execute(
                    command2,              // command
                    this.workingDirectory, // workingDirectory
                    in,                    // stdin
                    false,                 // closeStdin
                    System.out,            // stdout
                    false,                 // closeStdout
                    System.err,            // stderr
                    false                  // closeStderr
                );
            } catch (Exception e) {
                throw ExceptionUtil.wrap("Running 'pipe' on '" + properties + "'", e, RuntimeException.class);
            }
        }

        @Override public String
        toString() { return "(pipe contents to command " + this.command + ")"; }
    }

    /**
     * Disassembles a Java class file.
     */
    public static
    class DisassembleAction implements Action {

        private final boolean        verbose;
        @Nullable private final File sourceDirectory;
        private final boolean        hideLines;
        private final boolean        hideVars;
        private final boolean        symbolicLabels;
        @Nullable private final File toFile;

        DisassembleAction(
            boolean        verbose,
            @Nullable File sourceDirectory,
            boolean        hideLines,
            boolean        hideVars,
            boolean        symbolicLabels,
            @Nullable File toFile
        ) {
            this.verbose         = verbose;
            this.sourceDirectory = sourceDirectory;
            this.hideLines       = hideLines;
            this.hideVars        = hideVars;
            this.symbolicLabels  = symbolicLabels;
            this.toFile          = toFile;
        }
        @Override public boolean
        evaluate(Mapping<String, Object> properties) {

            final Disassembler disassembler = new Disassembler();

            disassembler.setVerbose(this.verbose);
            disassembler.setSourcePath(new File[] { this.sourceDirectory });
            disassembler.setShowLineNumbers(!this.hideLines);
            disassembler.setShowVariableNames(!this.hideVars);
            disassembler.setSymbolicLabels(this.symbolicLabels);

            final InputStream in = Mappings.getNonNull(properties, "inputStream", InputStream.class);

            File toFile = this.toFile;

            try {
                if (toFile == null) {
                    disassembler.disasm(in);
                } else {
                    IoUtil.outputFileOutputStream(
                        toFile,                                                // file
                        new ConsumerWhichThrows<OutputStream, IOException>() { // delegate

                            @Override public void
                            consume(OutputStream os) throws IOException {
                                disassembler.setOut(os);
                                disassembler.disasm(in);
                            }
                        },
                        true                                                   // createMissingParentDirectories
                    );
                }
            } catch (IOException ioe) {
                return false;
            }

            return true;
        }

        @Override public String
        toString() { return "(Disassemble .class file)"; }
    }

    /**
     * Calculates a "message digest" of an input stream's content and prints it to {@link Printers#info(String)}.
     */
    public static
    class DigestAction implements Action {

        private final String algorithm;

        DigestAction(String algorithm) {
            this.algorithm = algorithm;
        }

        @Override public boolean
        evaluate(Mapping<String, Object> properties) {

            MessageDigest md;
            try {
                md = MessageDigest.getInstance(this.algorithm);
            } catch (NoSuchAlgorithmException nsae) {
                throw ExceptionUtil.wrap(
                    "Running '-digest' on '" + properties + "'",
                    nsae,
                    IllegalArgumentException.class
                );
            }

            InputStream is = Mappings.getNonNull(properties, "inputStream", InputStream.class);

            try {
                DigestAction.updateAll(md, is);
            } catch (IOException ioe) {
                throw ExceptionUtil.wrap("Running '-digest' on '" + properties + "'", ioe, RuntimeException.class);
            }

            byte[] digest = md.digest();

            Formatter f = new Formatter();
            for (byte b : digest) {
                f.format("%02x", b & 0xff);
            }

            Printers.info(f.toString());

            return true;
        }

        /**
         * Updates the <var>messageDigest</var> from the remaining content of the <var>inputStream</var>.
         */
        private static void
        updateAll(MessageDigest messageDigest, InputStream inputStream) throws IOException {
            byte[] buffer = new byte[8192];

            for (;;) {
                int n = inputStream.read(buffer);
                if (n == -1) return;
                messageDigest.update(buffer, 0, n);
            }
        }

        @Override public String
        toString() { return "(digest " + this.algorithm + ")"; }
    }

    /**
     * Calculates a "checksum" of an input stream's content and prints it to {@link Printers#info(String)}.
     */
    public static
    class ChecksumAction implements Action {

        enum ChecksumType {

            /**
             * @see java.util.zip.CRC32
             */
            CRC32   { @Override Checksum newChecksum() { return new java.util.zip.CRC32();   } },

            /**
             * @see java.util.zip.Adler32
             */
            ADLER32 { @Override Checksum newChecksum() { return new java.util.zip.Adler32(); } },
            ;

            /**
             * @return A new {@link Checksum} of this type
             */
            abstract Checksum newChecksum();
        }

        private final ChecksumType checksumType;

        ChecksumAction(ChecksumType checksumType) {
            this.checksumType = checksumType;
        }

        @Override public boolean
        evaluate(Mapping<String, Object> properties) {

            Checksum cs = this.checksumType.newChecksum();

            InputStream is = Mappings.getNonNull(properties, "inputStream", InputStream.class);

            try {
                ChecksumAction.updateAll(cs, is);
            } catch (IOException ioe) {
                throw ExceptionUtil.wrap("Running '-checksum' on '" + properties + "'", ioe, RuntimeException.class);
            }

            Printers.info(Long.toHexString(cs.getValue()));

            return true;
        }

        /**
         * Updates the <var>checksum</var> from the remaining content of the <var>inputStream</var>.
         */
        private static void
        updateAll(Checksum checksum, InputStream inputStream) throws IOException {
            byte[] buffer = new byte[8192];

            for (;;) {
                int n = inputStream.read(buffer);
                if (n == -1) return;
                checksum.update(buffer, 0, n);
            }
        }

        @Override public String
        toString() { return "(checksum " + this.checksumType + ")"; }
    }

    /**
     * Sets the "prune flag".
     */
    public static
    class PruneAction implements Action {

        @Override public boolean
        evaluate(Mapping<String, Object> properties) {

            boolean[] prune = Mappings.get(properties, Find.PRUNE_PROPERTY_NAME, boolean[].class);

            // "-prune"-ing has an effect only in some contexts (namely when directories are recursed).
            if (prune != null) prune[0] = true;

            return true;
        }

        @Override public String
        toString() { return "(prune)"; }
    }

    /**
     * Delete the current file or directory.
     */
    public static
    class DeleteAction implements Action {

        @Override public boolean
        evaluate(Mapping<String, Object> properties) {

            File file = (File) properties.get("file");
            if (file == null) {
                throw new RuntimeException("\"-delete\" is only possible on files (and not on archive entries)");
            }

            if (!FileUtil.attemptToDeleteRecursively(file)) {
                System.err.printf("Could not remove file \"%s\"", file.toString());
                return false;
            }

            return true;
        }

        @Override public String
        toString() { return "(delete)"; }
    }

    /**
     * Executes the search in STDIN, with path "-".
     * <p>
     *   This method is thread-safe.
     * </p>
     */
    public void
    findInStream(InputStream is) throws IOException {

        if (this.maxDepth < 0) return;

        this.findInStream("-", System.in, Mappings.<String, Object>mapping(

            // SUPPRESS CHECKSTYLE Wrap:7
            "isDirectory",      false,
            "isExecutable",     false,
            "isReadable",       true,
            "isWritable",       false,
            "lastModifiedDate", new Date(),
            "path",             "-",
            "size",             -1L
        ), 0);
    }

    /**
     * Executes the search in the <var>resource</var> (which may be a normal file, a directory, or any other resource).
     * <p>
     *   This method is thread-safe.
     * </p>
     */
    public void
    findInResource(String path, URL resource) throws IOException {
        this.findInResource(path, resource, 0);
    }

    /**
     * Executes the search in the <var>file</var> (which may be a normal file, or a directory).
     * <p>
     *   This method is thread-safe.
     * </p>
     */
    public void
    findInFile(File file) throws IOException {
        this.findInResource(file.getPath(), file.toURI().toURL(), 0);
    }

    private void
    findInDirectory(final String directoryPath, final File directory, final int currentDepth)
    throws IOException {

        Find.LOGGER.log(
            Level.FINER,
            "Processing directory \"{0}\" (path is \"{1}\")",
            new Object[] { directory, directoryPath }
        );

        final boolean[] prune = new boolean[1];

        RunnableUtil.swapIf(
            this.descendantsFirst,
            new RunnableWhichThrows<IOException>() {

                @Override public void
                run() {

                    // Evaluate the FIND expression for the directory.
                    Find.this.evaluateExpression(Mappings.augment(
                        Find.fileProperties(directoryPath, directory),
                        "type",                   "directory", // SUPPRESS CHECKSTYLE Wrap:2
                        "depth",                  currentDepth,
                        Find.PRUNE_PROPERTY_NAME, prune // <= The "-prune" action will potentially change this value
                    ));
                }
            },
            new RunnableWhichThrows<IOException>() {

                @Override public void
                run() throws IOException {

                    // Process the directory's members.
                    if (!prune[0] && currentDepth < Find.this.maxDepth) {

                        String[] memberNames = directory.list();
                        if (memberNames == null) {

                            // MS WINDOWS 7: Read-protected directory produces:
                            // isDirectory() => true
                            // canRead()     => true
                            // list()        => null
                            // listFiles()   => null
                            throw new IOException(directory + ": Permission denied");
                        }

                        for (String memberName : memberNames) {
                            String memberPath = directoryPath + File.separatorChar + memberName;

                            try {
                                Find.this.findInResource(
                                    memberPath,
                                    new File(directory, memberName).toURI().toURL(),
                                    currentDepth + 1
                                );
                            } catch (IOException ioe) {
                                Find.this.exceptionHandler.consume(ExceptionUtil.wrap((
                                    "Continue with next directory member after member \""
                                    + memberPath
                                    + "\""
                                ), ioe));

                            }
                        }
                    }
                }
            }
        );
    }

    /**
     * Returns a mapping of all relevant properties of the given {@code file}.
     * <dl>
     *   <dt>{@code "absolutePath"}:</dt><dd>{@code String}</dd>
     *   <dt>{@code "canonicalPath"}:</dt><dd>{@code String}</dd>
     *   <dt>{@code "lastModifiedDate"}:</dt><dd>{@link Date}</dd>
     *   <dt>{@code "name"}:</dt><dd>{@code String}</dd>
     *   <dt>{@code "path"}:</dt><dd>{@code String}</dd>
     *   <dt>{@code "size"}:</dt><dd>{@code long}</dd>
     *   <dt>{@code "isDirectory"}:</dt><dd>{@code boolean}</dd>
     *   <dt>{@code "isFile"}:</dt><dd>{@code boolean}</dd>
     *   <dt>{@code "isHidden"}:</dt><dd>{@code boolean}</dd>
     *   <dt>{@code "isReadable"}:</dt><dd>{@code boolean}</dd>
     *   <dt>{@code "isWritable"}:</dt><dd>{@code boolean}</dd>
     *   <dt>{@code "isExecutable"}:</dt><dd>{@code boolean}</dd>
     *  </dl>
     */
    @SuppressWarnings("unused") public static Mapping<String, Object>
    fileProperties(final String path, final File file) {

        return Mappings.propertiesOf(new Object() {

            public String  getAbsolutePath()                     { return file.getAbsolutePath();        }
            public String  getCanonicalPath() throws IOException { return file.getCanonicalPath();       }
            public File    getFile()                             { return file;                          }
            public Date    getLastModifiedDate()                 { return new Date(file.lastModified()); }
            public String  getName()                             { return file.getName();                }
            public String  getPath()                             { return path;                          }
            public long    getSize()                             { return file.length();                 }
            public boolean isDirectory()                         { return file.isDirectory();            }
            public boolean isFile()                              { return file.isFile();                 }
            public boolean isHidden()                            { return file.isHidden();               }
            public boolean isReadable()                          { return file.canRead();                }
            public boolean isWritable()                          { return file.canWrite();               }
            public boolean isExecutable()                        { return file.canExecute();             }

            @Override public String toString() { return "File \"" + path + "\""; }
        });
    }

    @SuppressWarnings("unused") public static Mapping<String, Object>
    resourceProperties(final String path, final URL resource) {

        try {
            return Mappings.augment(
                Find.fileProperties(path, new File(resource.toURI())),
                "type", "file" // SUPPRESS CHECKSTYLE Wrap
            );
        } catch (URISyntaxException e) {
            throw new AssertionError(e);
        } catch (IllegalArgumentException iae) {

            // "resource" does not designate a FILE resource.
            ;
        }

        return Mappings.propertiesOf(new Object() {

            public String getType()         { return resource.getProtocol() + "-resource"; }
            public String getAuthority()    { return resource.getAuthority();              }
            public int    getDefaultPort()  { return resource.getDefaultPort();            }
            public String getExternalForm() { return resource.toExternalForm();            }
            public String getFile()         { return resource.getFile();                   }
            public String getHost()         { return resource.getHost();                   }
            public String getPath()         { return path;                                 }
            public int    getPort()         { return resource.getPort();                   }
            public String getProtocol()     { return resource.getProtocol();               }
            public String getQuery()        { return resource.getQuery();                  }
            public String getRef()          { return resource.getRef();                    }
            public String getUserInfo()     { return resource.getUserInfo();               }

            @Override public String toString() { return "Resource \"" + path + "\""; }
        });
    }

    public static de.unkrig.commons.text.expression.Expression
    parseExt(String spec) {

        ExpressionEvaluator ee = new ExpressionEvaluator(PredicateUtil.<String>always());

        try {
            return ee.parseExt(spec);
        } catch (ParseException pe) {
            throw ExceptionUtil.wrap("Parsing \"" + spec + "\"", pe, IllegalArgumentException.class);
        }
    }

    @Nullable private static String
    evaluateExpression(de.unkrig.commons.text.expression.Expression expression, Mapping<String, Object> variables) {

        // The default value for missing variables is "".
        variables = Mappings.union(variables, Mappings.<String, Object>constant(""));

        try {
            return expression.evaluateTo(
                variables,
                String.class
            );
        } catch (EvaluationException ee) {
            throw ExceptionUtil.wrap("Evaluating \"" + expression + "\"", ee, IllegalArgumentException.class);
        }
    }

    private void
    findInResource(final String path, final URL resource, final int currentDepth)
    throws IOException {

        Find.LOGGER.log(Level.FINER, "Processing \"{0}\" (path is \"{1}\")", new Object[] { resource, path });

        // Handle the special case when the resource designates a *directory*. ("CompressUtil.processFile()" can NOT
        // handle directories, only normal files!)
        {
            File file = ResourceProcessings.isFile(resource);
            if (file != null && file.isDirectory()) {
                this.findInDirectory(path, file, currentDepth);
                return;
            }
        }

        ArchiveHandler<Void>        archiveHandler;
        CompressorHandler<Void>     compressorHandler;
        NormalContentsHandler<Void> normalContentsHandler;
        {
            Mapping<String, Object> resourceProperties = Find.resourceProperties(path, resource);

            archiveHandler        = this.archiveHandler(path, resourceProperties, currentDepth);
            compressorHandler     = this.compressorHandler(path, resourceProperties, currentDepth);
            normalContentsHandler = this.normalContentsHandler(path, resourceProperties, currentDepth);
        }

        final File file = ResourceProcessings.isFile(resource);
        if (file != null) {
            CompressUtil.processFile(
                path,
                file,
                this.lookIntoFormat,
                archiveHandler,
                compressorHandler,
                normalContentsHandler
            );
        } else {
            InputStream is = resource.openStream();
            try {

                CompressUtil.processStream(
                    path,
                    is,
                    this.lookIntoFormat,
                    archiveHandler,
                    compressorHandler,
                    normalContentsHandler
                );
                is.close();
            } finally {
                try { is.close(); } catch (Exception e) {}
            }
        }
    }

    private void
    findInStream(
        final String            path,
        InputStream             inputStream,
        Mapping<String, Object> streamProperties,
        final int               currentDepth
    ) throws IOException {

        streamProperties = Mappings.override(
            streamProperties,
            "type", "contents", // SUPPRESS CHECKSTYLE Wrap:1
            "path", path
        );

        try {
            CompressUtil.processStream(
                path,                                                            // path
                inputStream,                                                     // inputStream
                Find.this.lookIntoFormat,                                        // lookIntoArchive
                this.archiveHandler(path, streamProperties, currentDepth),       // archiveHandler
                this.compressorHandler(path, streamProperties, currentDepth),    // compressorHandler
                this.normalContentsHandler(path, streamProperties, currentDepth) // normalContentsHandler
            );
        } catch (UnsupportedZipFeatureException uzfe) {

            // Cannot use "ExceptionUtil.wrap(prefix, cause)" here, because this exception
            // has none of the "usual" constructors.
            throw new IOException((
                path
                + "!"
                + uzfe.getEntry().getName()
                + ": Unsupported ZIP feature \""
                + uzfe.getFeature()
                + "\""
            ), uzfe);
        } catch (IOException ioe) {
            throw ExceptionUtil.wrap(path, ioe);
        } catch (RuntimeException re) {
            throw ExceptionUtil.wrap(path, re);
        }
    }

    private CompressorHandler<Void>
    compressorHandler(final String path, final Mapping<String, Object> properties, final int currentDepth) {

        return new CompressorHandler<Void>() {

            @Override @Nullable public Void
            handleCompressor(
                final CompressorInputStream compressorInputStream,
                final CompressionFormat     compressionFormat
            ) throws IOException {

                RunnableUtil.swapIf(
                    Find.this.descendantsFirst,
                    new RunnableWhichThrows<IOException>() {

                        @Override public void
                        run() {

                            // Evaluate the FIND expression for the compressed resource.
                            // Notice that we don't define an "inputStream" property, because otherwise we couldn't
                            // process the CONTENTS of the compressed resource.
                            Find.this.evaluateExpression(Mappings.override(
                                properties,
                                "type",              "compressed-" + properties.get("type"), // SUPPRESS CHECKSTYLE Wrap|LineLength:3
                                "path",              path,
                                "compressionFormat", compressionFormat,
                                "depth",             currentDepth
                            ));
                        }
                    },
                    new RunnableWhichThrows<IOException>() {

                        @Override public void
                        run() throws IOException {

                            // Process the compressed resource's contents.
                            if (currentDepth < Find.this.maxDepth) {
                                Object name = properties.get("name");
                                assert name != null;
                                Find.this.findInStream(
                                    path + '%',
                                    compressorInputStream,
                                    Mappings.override(
                                        properties,
                                        "compressionFormat", compressionFormat, // SUPPRESS CHECKSTYLE Wrap:3
                                        "name",              name + "%",
                                        "size",              -1L
                                    ),
                                    currentDepth + 1
                                );
                            }
                        }
                    }
                );

                return null;
            }
        };
    }

    private ArchiveHandler<Void>
    archiveHandler(final String path, final Mapping<String, Object> properties, final int currentDepth) {

        return new ArchiveHandler<Void>() {

            @Override @Nullable public Void
            handleArchive(final ArchiveInputStream archiveInputStream, final ArchiveFormat archiveFormat)
            throws IOException {

                final boolean[] prune = new boolean[1];

                RunnableUtil.swapIf(
                    Find.this.descendantsFirst,
                    new RunnableWhichThrows<IOException>() {

                        @Override public void
                        run() {

                            // Evaluate the FIND expression for the archive resource.
                            Find.this.evaluateExpression(Mappings.override(
                                properties,
                                "type",                   "archive-" + properties.get("type"), // SUPPRESS CHECKSTYLE Wrap|LineLength:3
                                "archiveFormat",          archiveFormat,
                                "depth",                  currentDepth,
                                Find.PRUNE_PROPERTY_NAME, prune[0] // <= The "-prune" action will potentially change this value
                            ));
                        }
                    },
                    new RunnableWhichThrows<IOException>() {

                        @Override public void
                        run() throws IOException {

                            if (prune[0] || currentDepth >= Find.this.maxDepth) return;

                            // Process the archive's entries.
                            for (
                                ArchiveEntry ae = archiveInputStream.getNextEntry();
                                ae != null;
                                ae = archiveInputStream.getNextEntry()
                            ) {

                                String entryName = ArchiveFormatFactory.normalizeEntryName(ae.getName());
                                String entryPath = path + '!' + entryName;

                                if (ae.isDirectory()) {

                                    // Evaluate the FIND expression for the directory entry.
                                    Find.this.evaluateExpression(Mappings.override(
                                        Mappings.union(Mappings.propertiesOf(ae), properties),
                                        "path",          entryPath, // SUPPRESS CHECKSTYLE Wrap:4
                                        "name",          entryName,
                                        "archiveFormat", archiveFormat,
                                        "type",          "directory-entry",
                                        "depth",         currentDepth + 1
                                    ));
                                } else {
                                    try {
                                        Find.this.findInStream(
                                            entryPath,
                                            archiveInputStream,
                                            Mappings.override(
                                                Mappings.union(Mappings.propertiesOf(ae), properties),
                                                "archiveFormat", archiveFormat // SUPPRESS CHECKSTYLE Wrap:1
                                            ),
                                            currentDepth + 1
                                        );
                                    } catch (IOException ioe) {
                                        Find.this.exceptionHandler.consume(ExceptionUtil.wrap((
                                            "Continue with next "
                                            + archiveFormat
                                            + " archive entry after entry \""
                                            + entryPath
                                            + "\""
                                        ), ioe));
                                    }
                                }
                            }
                        }
                    }
                );

                return null;
            }
        };
    }

    private NormalContentsHandler<Void>
    normalContentsHandler(final String path, final Mapping<String, Object> properties, final int currentDepth) {

        return new NormalContentsHandler<Void>() {

            @Override @Nullable public Void
            handleNormalContents(final InputStream inputStream) {

                // Evaluate the FIND expression for the nested normal contents.
                Find.this.evaluateExpression(Mappings.override(
                    properties,
                    "path",        path,              // SUPPRESS CHECKSTYLE Wrap:5
                    "type",        "normal-" + properties.get("type"),
                    "inputStream", inputStream,
                    "depth",       currentDepth,
                    "size",        new Producer<Long>() {

                        @Override @Nullable public Long
                        produce() {

                            // Check if the "size" property inherited from the ArchiveEntry has a reasonable
                            // value (ZipArchiveEntries have size -1 iff the archive was created in "streaming
                            // mode").
                            Long size = (Long) properties.get("size");
                            assert size != null;
                            if (size != -1) return size;

                            // Compute the value of the "size" property only IF it is needed, and WHEN it is
                            // needed, because it consumes the contents.
                            try {
                                return InputStreams.skipAll(inputStream);
                            } catch (IOException ioe) {
                                throw ExceptionUtil.wrap(
                                    "Measuring size of \"" + path + "\"",
                                    ioe,
                                    RuntimeException.class
                                );
                            }
                        }
                    }
                ));

                return null;
            }
        };
    }

    private void
    evaluateExpression(Mapping<String, Object> properties) {

        // Do not evaluate the expression if the current depth is less than "this.minDepth".
        if (this.minDepth > 0) {

            Object depthValue = properties.get("depth");
            assert depthValue instanceof Integer;

            int currentDepth = (Integer) depthValue;

            if (currentDepth < this.minDepth) return;
        }

        this.expression.evaluate(properties);
    }
}
