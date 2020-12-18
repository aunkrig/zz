
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

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.CRC32;
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
import de.unkrig.commons.lang.protocol.Function;
import de.unkrig.commons.lang.protocol.Mapping;
import de.unkrig.commons.lang.protocol.Mappings;
import de.unkrig.commons.lang.protocol.Predicate;
import de.unkrig.commons.lang.protocol.PredicateUtil;
import de.unkrig.commons.lang.protocol.Producer;
import de.unkrig.commons.lang.protocol.ProducerUtil;
import de.unkrig.commons.lang.protocol.RunnableUtil;
import de.unkrig.commons.lang.protocol.RunnableWhichThrows;
import de.unkrig.commons.nullanalysis.Nullable;
import de.unkrig.commons.text.Printers;
import de.unkrig.commons.text.expression.EvaluationException;
import de.unkrig.commons.text.expression.ExpressionEvaluator;
import de.unkrig.commons.text.parser.ParseException;
import de.unkrig.commons.text.pattern.Glob;
import de.unkrig.commons.text.pattern.Pattern2;
import de.unkrig.commons.util.collections.MapUtil;
import de.unkrig.jdisasm.ClassFile;
import de.unkrig.jdisasm.Disassembler;

/**
 * The public API for the ZZFIND functionality.
 * <p>
 *   {@link #Find()} creates a default configuration.
 * </p>
 * <p>
 *   These setters change the configuration:
 * </p>
 * <ul>
 *   <li>{@link #setDescendantsFirst(boolean)}</li>
 *   <li>{@link #setExceptionHandler(ConsumerWhichThrows)}</li>
 *   <li>{@link #setExpression(Expression)}</li>
 *   <li>{@link #setLookIntoFormat(Predicate)}</li>
 *   <li>{@link #setMinDepth(int)}</li>
 *   <li>{@link #setMaxDepth(int)}</li>
 * </ul>
 * <p>
 *   These methods execute a search and honor the current configuration:
 * </p>
 * <ul>
 *   <li>{@link #findInFile(File)}</li>
 *   <li>{@link #findInResource(String, URL)}</li>
 *   <li>{@link #findInStream(InputStream)}</li>
 * </ul>
 * <p>
 *   By convention, expressions produce output (if any) with {@link Printers#info(String)}, so invokers of the {@code
 *   find...()} methods can catch that output like
 * <p>
 * <pre>
 *     List&lt;String> lines = new ArrayList&lt;>();
 *
 *     AbstractPrinter.getContextPrinter().redirectInfo(
 *         ConsumerUtil.addToCollection(lines)
 *     ).run((RunnableWhichThrows&lt;IOException>) () -> find.findInFile(myFile));
 * </pre>
 */
public
class Find {

    static { AssertionUtil.enableAssertionsForThisClass(); }

    private static final Logger LOGGER = Logger.getLogger(Find.class.getName());

    private static final String PRUNE_PROPERTY_NAME = "$PRUNE";

    // BEGIN CONFIGURATION VARIABLES

    private Predicate<? super String> lookIntoFormat = PredicateUtil.always();
    private boolean                   descendantsFirst;
    private int                       minDepth;
    private int                       maxDepth = Integer.MAX_VALUE;

    /**
     * The expression to match files/entries against.
     */
    private Expression expression = Test.TRUE;

    private ConsumerWhichThrows<? super IOException, ? extends IOException>
    exceptionHandler = ConsumerUtil.throwsSubject();

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

        // SUPPRESS CHECKSTYLE LineLength:27
        /**
         * Evaluates to {@code true} or {@code false}, depending on the <var>properties</var>. The available properties
         * depend on the type of the current file or resource. E.g. files provide a "file" variable, and HTTP resources
         * a "url" variable.
         * <p>
         *   For other variables, see the JAVADOC of {@link Main#main(String[])}.
         * </p>
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
         *   <dt>Age 0:00:00.000 ... 23:59:59.999:</dt>
         *   <dd>Value 0</dd>
         *
         *   <dt>Age 24:00:00.000 ... 47:59:59.999:</dt>
         *   <dd>Value 1</dd>
         *
         *   <dt>Age 48:00:00.000 ... 71:59:59.999:</dt>
         *   <dd>Value 2</dd>
         * </dl>
         * etc.
         * <p>
         *   Iff <var>factor</var> is {@link #MINUTES}:
         * </p>
         * <dl>
         *   <dt>Age 0:00:00.000 ... 0:00:59.999:</dt>
         *   <dd>Value 0</dd>
         *
         *   <dt>Age 0:01:00.000 ... 00:01:59.999:</dt>
         *   <dd>Value 1</dd>
         *
         *   <dt>Age 0:02:00.000 ... 00:02:59.999:</dt>
         *   <dd>Value 2</dd>
         * </dl>
         * etc.
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

        public
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
                    throw ExceptionUtil.wrap("Parsing '-printf " + argExpression + "'", pe, RuntimeException.class);
                } catch (EvaluationException ee) {
                    throw ExceptionUtil.wrap("Evaluating '-printf " + argExpression + "'", ee, RuntimeException.class);
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
     * Prints the file type ('d', 'a', 'D' or '-'), readability ('r' or '-'), writability ('w' or '-'), size,
     * modification time and path to the given {@link Writer} and evaluates to {@code true}.
     */
    public static
    class LsAction implements Action {

        @Override public boolean
        evaluate(Mapping<String, Object> properties) {

            String type = Mappings.getNonNull(properties, "type", String.class);
            File   file = Mappings.get(properties, "file", File.class);
            Long   size = Mappings.get(properties, "size", Long.class);

            char c1 = (
                "directory".equals(type)       ? 'd' :
                type.startsWith("archive-")    ? 'a' :
                "directory-entry".equals(type) ? 'D' :
                '-'
            );
            boolean isReadable   = file != null ? file.canRead()    : true;
            boolean isWritable   = file != null ? file.canWrite()   : false;
            boolean isExecutable = file != null ? file.canExecute() : false;

            Printers.info(String.format(
                "%c%c%c%c %10d %-10tF %<-8tT %s",
                c1,
                isReadable   ? 'r' : '-',
                isWritable   ? 'w' : '-',
                isExecutable ? 'x' : '-',
                size != null ? size : 0L,
                Mappings.get(properties, "lastModifiedDate", Date.class),
                Mappings.getNonNull(properties, "path", String.class)
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
            InputStream is = Mappings.get(properties, "inputStream", InputStream.class);
            if (is == null) {
                throw new RuntimeException(
                    "\"-cat\" is only possible on \"normal-*\"-type contents "
                    + "(and not on directories, dir-type archive entries, compressed content etc.)"
                );
            }
            try {
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

            String pathname = Find.evaluateExpression(this.tofile, properties);
            assert pathname != null;

            File tofile = new File(pathname);
            assert tofile != null;

            tofile = Find.fixFile(tofile);

            try {

                if (this.mkdirs) IoUtil.createMissingParentDirectoriesFor(tofile);

                InputStream in = Mappings.get(properties, "inputStream", InputStream.class);
                if (in == null) {
                    throw new RuntimeException(
                        "\"-copy\" is only possible on \"normal-*\"-type contents "
                        + "(and not on directories, dir-type archive entries, compressed content etc.)"
                    );
                }

                OutputStream out = new FileOutputStream(tofile);
                try {
                    IoUtil.copy(in, out);
                    out.close();
                } finally {
                    try { out.close(); } catch (IOException e) {}
                }
            } catch (IOException ioe) {
                throw ExceptionUtil.wrap(
                    "Copying to \"" + tofile + "\"",
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

            final InputStream in = Mappings.get(properties, "inputStream", InputStream.class);
            if (in == null) {
                throw new RuntimeException(
                    "\"-pipe\" is only possible on \"normal-*\"-type contents "
                    + "(and not on directories, dir-type archive entries, compressed content etc.)"
                );
            }


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

            final InputStream in = Mappings.get(properties, "inputStream", InputStream.class);
            if (in == null) {
                throw new RuntimeException(
                    "\"-disassemble\" is only possible on \"normal-*\"-type contents "
                    + "(and not on directories, dir-type archive entries, compressed content etc.)"
                );
            }

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
     * Disassembles a Java class file.
     */
    public static
    class JavaClassFileAction implements Action {

        private final de.unkrig.commons.text.expression.Expression expression;

        JavaClassFileAction(String expression) {
            this.expression = Find.parse(expression);
        }

        @Override public boolean
        evaluate(Mapping<String, Object> properties) {

            final InputStream in = Mappings.get(properties, "inputStream", InputStream.class);
            if (in == null) {
                throw new RuntimeException(
                    "\"-java-class-file\" is only possible on \"normal-*\"-type contents "
                    + "(and not on directories, dir-type archive entries, compressed content etc.)"
                );
            }

            ClassFile cf;
            try {
                cf = new ClassFile(new DataInputStream(in));
            } catch (IOException ioe) {
                return false;
            }
            System.out.println(Find.evaluateExpression(this.expression, Mappings.override(properties, "cf", cf)));

            return true;
        }

        @Override public String
        toString() { return "(Java class file)"; }
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

            InputStream is = Mappings.get(properties, "inputStream", InputStream.class);
            if (is == null) {
                throw new RuntimeException(
                    "\"-digest\" is only possible on \"normal-*\"-type contents "
                    + "(and not on directories, dir-type archive entries, compressed content etc.)"
                );
            }

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
            Printers.info(Long.toHexString(Find.checksum(properties, this.checksumType.newChecksum())));
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

        final Map<String, Producer<? extends Object>> properties = new HashMap<String, Producer<? extends Object>>();
        properties.put("path", Find.cp("-"));
        properties.put("size", Find.cp(-1L));

        this.findInStream("-", System.in, null, properties, 0);
    }

    public static File
    fixFile(File f) {

        String pn      = f.getPath();
        String fixedPn = Find.fixPathname(pn);

        return fixedPn.contentEquals(pn) ? f : new File(fixedPn);
    }

    private static String
    fixPathname(String pn) {

        // Some file systems do not tolerate specific characters in path names, e.g. MS WINDOWS doesn't accept colons.
        // That would cause an exception in the "-copy" action. In most cases, we rather want to replace the forbidden
        // character with some string; this can be configured through this system property:

        if (Find.REPLACE_COLON_WITH != null) {
            pn = pn.replace(":", Find.REPLACE_COLON_WITH);
        }

        return pn;
    }
    @Nullable private static final String REPLACE_COLON_WITH = System.getProperty("Find.replaceColonWith");

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
                    Map<String, Producer<? extends Object>> properties2 = new HashMap<String, Producer<? extends Object>>();
                    properties2.put("type",                   Find.cp("directory"));
                    properties2.put("name",                   Find.cp(directory.getName()));
                    properties2.put("path",                   Find.cp(directoryPath));
                    properties2.put("file",                   Find.cp(directory));
                    properties2.put("depth",                  Find.cp(currentDepth));
                    properties2.put("inputStream",            Find.cp(null));
                    properties2.put("readable",               () -> directory.canRead());
                    properties2.put("writable",               () -> directory.canWrite());
                    properties2.put("executable",             () -> directory.canExecute());
                    properties2.put("lastModified",           () -> directory.lastModified());
                    properties2.put("lastModifiedDate",       () -> new Date(directory.lastModified()));
                    properties2.put(Find.PRUNE_PROPERTY_NAME, Find.cp(prune)); // <= The "-prune" action will potentially change this value

                    Find.this.evaluateExpression(properties2);
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

                            // JRE11+MS WINDOWS replace colons (#003A) in member names with #F03A, for whatever reason.
                            memberName = memberName.replace((char) 0xf031, ':');

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

    public static de.unkrig.commons.text.expression.Expression
    parse(String spec) {

        ExpressionEvaluator ee = new ExpressionEvaluator(PredicateUtil.<String>always());

        try {
            return ee.parse(spec);
        } catch (ParseException pe) {
            throw ExceptionUtil.wrap("Parsing \"" + spec + "\"", pe, IllegalArgumentException.class);
        }
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

        Map<String, Producer<? extends Object>> resourceProperties = new HashMap<String, Producer<? extends Object>>();
        {
            File file = ResourceProcessings.isFile(resource);
            if (file != null) {

                if (file.isDirectory()) {

                    // Handle the special case when the resource designates a *directory*. ("CompressUtil.processFile()" can NOT
                    // handle directories, only normal files!)
                    this.findInDirectory(path, file, currentDepth);
                    return;
                }
                resourceProperties.put("type",             Find.cp("file"));
                resourceProperties.put("name",             Find.cp(file.getName()));
                resourceProperties.put("size",             () -> file.length());
                resourceProperties.put("readable",         () -> file.canRead());
                resourceProperties.put("writable",         () -> file.canWrite());
                resourceProperties.put("executable",       () -> file.canExecute());
                resourceProperties.put("inputStream",      () -> {
                    try {
                        return new FileInputStream(file);
                    } catch (FileNotFoundException fnfe) {
                        throw ExceptionUtil.wrap(
                            "Reading file \"" + resource + "\"",
                            fnfe,
                            RuntimeException.class
                        );
                    }
                });
                resourceProperties.put("crc",              () -> {
                    final CRC32 cs = new java.util.zip.CRC32();
                    try (InputStream is = new FileInputStream(file)) {
                        ChecksumAction.updateAll(cs, is);
                    } catch (IOException ioe) {
                        throw ExceptionUtil.wrap(
                            "Computing CRC of file \"" + resource + "\"",
                            ioe,
                            RuntimeException.class
                        );
                    }
                    return (int) cs.getValue();
                });
                resourceProperties.put("file",             Find.cp(file));
                resourceProperties.put("lastModified",     () -> file.lastModified());
                resourceProperties.put("lastModifiedDate", () -> new Date(file.lastModified()));
            } else {
                resourceProperties.put("type",             Find.cp(resource.getProtocol() + "-resource"));
                String up = resource.getPath();
                resourceProperties.put("name",             Find.cp(up.substring(up.lastIndexOf('/') + 1)));
                resourceProperties.put("size",             () -> {
                    try {
                        return resource.openConnection().getContentLengthLong();
                    } catch (IOException ioe) {
                        throw ExceptionUtil.wrap(
                            "Querying size of resource \"" + resource + "\"",
                            ioe,
                            RuntimeException.class
                        );
                    }
                });
                resourceProperties.put("readable",         Find.cp(true));
                resourceProperties.put("writable",         Find.cp(false));
                resourceProperties.put("executable",       Find.cp(false));
                resourceProperties.put("crc",              () -> {
                    final CRC32 cs = new java.util.zip.CRC32();
                    try (InputStream is = resource.openConnection().getInputStream()) {
                        ChecksumAction.updateAll(cs, is);
                    } catch (IOException ioe) {
                        throw ExceptionUtil.wrap(
                            "Computing CRC of resource \"" + resource + "\"",
                            ioe,
                            RuntimeException.class
                        );
                    }
                    return (int) cs.getValue();
                });
            }
        }

        ArchiveHandler<Void>        archiveHandler;
        CompressorHandler<Void>     compressorHandler;
        NormalContentsHandler<Void> normalContentsHandler;
        {
//            Mapping<String, Object> resourceProperties = Find.resourceProperties(path, resource);

            resourceProperties.put("url",  Find.cp(resource));
            resourceProperties.put("path", Find.cp(path));

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
            URLConnection conn             = resource.openConnection();
            InputStream   is               = conn.getInputStream();
            long          lastModified     = conn.getLastModified();
            Date          lastModifiedDate = lastModified == 0 ? null : new Date(lastModified);
            try {

                CompressUtil.processStream(
                    path,                 // path
                    is,                   // inputStream
                    lastModifiedDate,     // lastModifiedDate
                    this.lookIntoFormat,  // lookIntoFormat
                    archiveHandler,       // archiveHandler
                    compressorHandler,    // compressorHandler
                    normalContentsHandler // normalContentsHandler
                );
                is.close();
            } finally {
                try { is.close(); } catch (Exception e) {}
            }
        }
    }

    private void
    findInStream(
        final String                            path,
        InputStream                             inputStream,
        @Nullable Date                          lastModifiedDate,
        Map<String, Producer<? extends Object>> streamProperties,
        final int                               currentDepth
    ) throws IOException {

        streamProperties = new HashMap<String, Producer<? extends Object>>(streamProperties);
        streamProperties.put("type", Find.cp("contents"));
        streamProperties.put("path", Find.cp(path));

        try {
            CompressUtil.processStream(
                path,                                                            // path
                inputStream,                                                     // inputStream
                lastModifiedDate,                                                // lastModifiedDate
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
    compressorHandler(
        final String                                  path,
        final Map<String, Producer<? extends Object>> properties,
        final int                                     currentDepth
    ) {

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

                            Map<String, Producer<? extends Object>> properties2 = new HashMap<String, Producer<? extends Object>>();
                            properties2.put("type",              Find.cp("compressed-" + properties.get("type").produce()));
                            properties2.put("name",              properties.get("name"));
                            properties2.put("readable",          Find.cp(true));
                            properties2.put("writable",          Find.cp(false));
                            properties2.put("executable",        Find.cp(false));
                            properties2.put("path",              Find.cp(path));
                            properties2.put("compressionFormat", Find.cp(compressionFormat));
                            properties2.put("depth",             Find.cp(currentDepth));
                            // We define "no input stream", because otherwise we couldn't process the CONTENTS of the
                            // compressed resource.
                            properties2.put("inputStream",       Find.cp(null));
                            Find.copyOptionalProperty(properties, "file",             properties2);
                            Find.copyOptionalProperty(properties, "lastModified",     properties2);
                            Find.copyOptionalProperty(properties, "lastModifiedDate", properties2);

                            Find.this.evaluateExpression(properties2);
                        }
                    },
                    new RunnableWhichThrows<IOException>() {

                        @Override public void
                        run() throws IOException {

                            // Process the compressed resource's contents.
                            if (currentDepth < Find.this.maxDepth) {
                                Producer<? extends Object> vg = properties.get("name");
                                assert vg != null;
                                Object name = vg.produce();
                                assert name != null;

                                Map<String, Producer<? extends Object>> properties2 = new HashMap<String, Producer<? extends Object>>();
                                properties2.put("compressionFormat", Find.cp(compressionFormat));
                                properties2.put("name",              Find.cp(name + "%"));
                                properties2.put("size",              Find.cp(-1L));
                                Find.copyProperty(properties, "readable",   properties2);
                                Find.copyProperty(properties, "writable",   properties2);
                                Find.copyProperty(properties, "executable", properties2);
                                Find.copyOptionalProperty(properties, "lastModified",     properties2);
                                Find.copyOptionalProperty(properties, "lastModifiedDate", properties2);

                                // "Inherit" lastModifiedDate from compression container.
                                Date lastModifiedDate = null;
                                {
                                    Producer<? extends Object> p = properties.get("lastModifiedDate");
                                    if (p != null) {
                                        Object o = p.produce();
                                        if (o instanceof Date) lastModifiedDate = (Date) o;
                                    }
                                }

                                Find.this.findInStream(
                                    path + '%',
                                    compressorInputStream,
                                    lastModifiedDate,
                                    properties2,
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
    archiveHandler(
        final String                                  path,
        final Map<String, Producer<? extends Object>> properties,
        final int                                     currentDepth
    ) {

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
                            Map<String, Producer<? extends Object>> properties2 = new HashMap<String, Producer<? extends Object>>();
                            properties2.put("type",                   Find.cp("archive-" + properties.get("type").produce()));
                            properties2.put("path",                   Find.cp(path));
                            properties2.put("archiveFormat",          Find.cp(archiveFormat));
                            properties2.put("depth",                  Find.cp(currentDepth));
                            properties2.put("inputStream",            Find.cp(null));
                            properties2.put("name",                   properties.get("name"));
                            properties2.put(Find.PRUNE_PROPERTY_NAME, Find.cp(prune));
                            Find.copyProperty(properties, "size",        properties2);
                            Find.copyProperty(properties, "readable",    properties2);
                            Find.copyProperty(properties, "writable",    properties2);
                            Find.copyProperty(properties, "executable",  properties2);
                            Find.copyOptionalProperty(properties, "file",              properties2);
                            Find.copyOptionalProperty(properties, "lastModified",      properties2);
                            Find.copyOptionalProperty(properties, "lastModifiedDate",  properties2);
                            Find.copyOptionalProperty(properties, "compressionMethod", properties2);

                            Find.this.evaluateExpression(properties2);
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
                                try {
                                    this.processEntry(archiveInputStream, archiveFormat, ae);
                                } catch (RuntimeException re) {
                                    throw ExceptionUtil.wrap("Processing archive entry \"" + ae.getName() + "\"", re);
                                }
                            }
                        }

                        private void
                        processEntry(
                            final ArchiveInputStream archiveInputStream,
                            final ArchiveFormat      archiveFormat,
                            ArchiveEntry             ae
                        ) throws IOException {
                            String entryName = ArchiveFormatFactory.normalizeEntryName(ae.getName());
                            String entryPath = path + '!' + entryName;

                            Producer<Object> crcGetter = Find.methodPropertyGetter(ae, "getCrc");
                            if (crcGetter == null) crcGetter = Find.cp(-1);

                            Date lastModifiedDate;
                            long lastModified;
                            try {
                                lastModifiedDate = ae.getLastModifiedDate();
                                lastModified     = lastModifiedDate.getTime();
                            } catch (UnsupportedOperationException uoe) {

                                // Some ArchiveEntry implementations (e.g. SevenZArchiveEntry) throw UOE when "a
                                // last modified date is not set".
                                lastModifiedDate = null;
                                lastModified     = 0;
                            }

                            Map<String, Producer<? extends Object>> properties2 = new HashMap<String, Producer<? extends Object>>();
                            properties2.put("lastModifiedDate",  Find.cp(lastModifiedDate));
                            properties2.put("lastModified",      Find.cp(lastModified));
                            properties2.put("name",              Find.cp(ae.getName()));
                            properties2.put("size",              Find.cp(ae.getSize()));
                            properties2.put("readable",          Find.cp(true));
                            properties2.put("writable",          Find.cp(false));
                            properties2.put("executable",        Find.cp(false));
                            properties2.put("crc",               crcGetter);
                            properties2.put("compressionMethod", Find.cp(archiveFormat.getCompressionMethod(ae)));

//                                Find.putAllPropertiesOf(ae, Find.PROPERTIES_OF_ARCHIVE_ENTRY, properties2);
                            if (ae.isDirectory()) {

                                // Evaluate the FIND expression for the directory entry.
                                properties2.put("path",          Find.cp(entryPath));
                                properties2.put("name",          Find.cp(entryName));
                                properties2.put("archiveFormat", Find.cp(archiveFormat));
                                properties2.put("type",          Find.cp("directory-entry"));
                                properties2.put("depth",         Find.cp(currentDepth + 1));
                                properties2.put("inputStream",   Find.cp(null));

                                // Archive formate are inconsistent withe the "size" of a directory entry --
                                // sometimes it computes to 0, sometimes to -1. We always want 0.
                                properties2.put("size", Find.cp(0L));

                                Find.this.evaluateExpression(properties2);
                            } else {

                                // Evaluate the FIND expression for the non-directory entry.
                                properties2.put("archiveFormat", Find.cp(archiveFormat));

                                try {
                                    Find.this.findInStream(
                                        entryPath,
                                        archiveInputStream,
                                        lastModifiedDate,
                                        properties2,
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
                );

                return null;
            }
        };
    }

    private NormalContentsHandler<Void>
    normalContentsHandler(final String path, final Map<String, Producer<? extends Object>> properties, final int currentDepth) {

        return new NormalContentsHandler<Void>() {

            @Override @Nullable public Void
            handleNormalContents(final InputStream inputStream, @Nullable Date lastModifiedDate) {

                // Evaluate the FIND expression for the nested normal contents.
                Map<String, Producer<? extends Object>> properties2 = new HashMap<String, Producer<? extends Object>>(properties);
                properties2.put("path",             Find.cp(path));
                properties2.put("type",             () -> "normal-" + properties.get("type").produce());
                properties2.put("lastModifiedDate", () -> lastModifiedDate);
                Find.copyProperty(properties, "readable",   properties2);
                Find.copyProperty(properties, "writable",   properties2);
                Find.copyProperty(properties, "executable", properties2);
                properties2.put("inputStream",      Find.cp(inputStream));
                properties2.put("depth",            Find.cp(currentDepth));
                properties2.put("crc",              () -> {
                    final CRC32 cs = new java.util.zip.CRC32();
                    try {
                        ChecksumAction.updateAll(cs, inputStream);
                    } catch (IOException ioe) {
                        throw ExceptionUtil.wrap("Computing CRC of \"" + path + "\"", ioe, RuntimeException.class);
                    }
                    return (int) cs.getValue();
                });

                properties2.put("size",        () -> {

                    // Check if the "size" property inherited from the ArchiveEntry has a reasonable
                    // value (ZipArchiveEntries have size -1 iff the archive was created in "streaming
                    // mode").
                    Producer<? extends Object> sizeValueProducer = properties.get("size");
                    if (sizeValueProducer != null) {
                        Long size = (Long) sizeValueProducer.produce();
                        assert size != null;
                        if (size != -1) return size;
                    }

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
                });

                Find.this.evaluateExpression(properties2);

                return null;
            }
        };
    }

    private void
    evaluateExpression(Map<String, Producer<? extends Object>> properties) {

        // Do not evaluate the expression if the current depth is less than "this.minDepth".
        if (this.minDepth > 0) {

            Object depthValue = properties.get("depth").produce();
            assert depthValue instanceof Integer;

            int currentDepth = (Integer) depthValue;

            if (currentDepth < this.minDepth) return;
        }

        this.expression.evaluate(Find.toMapping(properties));
    }

    private static long
    checksum(final Mapping<String, Object> properties, Checksum cs) {

        InputStream is = Mappings.get(properties, "inputStream", InputStream.class);
        if (is == null) {
            throw new RuntimeException(
                "\"-checksum\" is only possible on \"normal-*\"-type contents "
                + "(and not on directories, dir-type archive entries, compressed content etc.)"
            );
        }

        try {
            ChecksumAction.updateAll(cs, is);
        } catch (IOException ioe) {
            throw ExceptionUtil.wrap("Running '-checksum' on '" + properties + "'", ioe, RuntimeException.class);
        } finally {
            try { is.close(); } catch (Exception e) {}
        }
        return cs.getValue();
    }

    private static Mapping<String, Object>
    toMapping(Map<String, Producer<? extends Object>> map) {

        return new Mapping<String, Object>() {

            @Nullable Map<String, Object> lazyMap;

            @Override public boolean
            containsKey(@Nullable Object key) {

                // We want undefined variables to default to NULL.
                //return "_keys".equals(key) || map.containsKey(key);
                return true;
            }

            @Override @Nullable public Object
            get(@Nullable Object key) {

                if ("_map".equals(key))    return this.getLazyMap();
                if ("_keys".equals(key))   return this.getLazyMap().keySet();
                if ("_values".equals(key)) return this.getLazyMap().values();

                Producer<? extends Object> valueProducer = map.get(key);
                if (valueProducer == null) return null;
                return valueProducer.produce();
            }

            private Map<String, Object>
            getLazyMap() {
                Map<String, Object> result = this.lazyMap;
                return result != null ? result : (result = Find.lazyMap(map));
            }
        };
    }

    private static Map<String, Object>
    lazyMap(Map<String, Producer<? extends Object>> map) {

        Map<String, Function<Object, Object>> functionMap = new HashMap<>();

        for (Entry<String, Producer<? extends Object>> e : map.entrySet()) {
            functionMap.put(e.getKey(), in -> e.getValue().produce());
        }

        return MapUtil.lazyMap(functionMap, null);
    }

    /**
     * @return {@code null} iff the <var>target</var> {@link HashMap} no non-static zero-parameter method with that
     *         <var>methodName</var>
     */
    @Nullable private static Producer<Object>
    methodPropertyGetter(Object target, String methodName) {

        Method method;
        try {
            method = target.getClass().getMethod(methodName);
        } catch (NoSuchMethodException e) {
            return null;
        } catch (Exception e) {
            throw ExceptionUtil.wrap(methodName, e, RuntimeException.class);
        }

        if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() > 0) return null;

        return () -> {
            try {
                return method.invoke(target);
            } catch (Exception e) {
                throw ExceptionUtil.wrap(methodName, e, RuntimeException.class);
            }
        };
    }

    private static <T> void
    copyProperty(
        Map<String, ? extends T> source,
        String                   propertyName,
        Map<String, ? super T>   destination
    ) {
        T value = source.get(propertyName);
        assert value != null || source.containsKey(propertyName) : propertyName;
        destination.put(propertyName, value);
    }

    private static <T> void
    copyOptionalProperty(
        Map<String, ? extends T> source,
        String                   propertyName,
        Map<String, ? super T>   destination
    ) {
        T value = source.get(propertyName);
        if (value != null) destination.put(propertyName, value);
    }

    /**
     * Shorthand for {@link ProducerUtil#constantProducer(Object)}.
     */
    private static <T> Producer<T>
    cp(@Nullable T constantValue) { return ProducerUtil.constantProducer(constantValue); }
}
