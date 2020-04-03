
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
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.unkrig.commons.lang.AssertionUtil;
import de.unkrig.commons.lang.ExceptionUtil;
import de.unkrig.commons.lang.protocol.Predicate;
import de.unkrig.commons.lang.protocol.ProducerWhichThrows;
import de.unkrig.commons.nullanalysis.Nullable;
import de.unkrig.commons.text.parser.AbstractParser;
import de.unkrig.commons.text.parser.ParseException;
import de.unkrig.commons.text.scanner.AbstractScanner;
import de.unkrig.commons.text.scanner.AbstractScanner.Token;
import de.unkrig.commons.text.scanner.ScanException;
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
import de.unkrig.zz.find.Find.PrintfAction;
import de.unkrig.zz.find.Find.PruneAction;
import de.unkrig.zz.find.Find.ReadabilityTest;
import de.unkrig.zz.find.Find.SizeTest;
import de.unkrig.zz.find.Find.Test;
import de.unkrig.zz.find.Find.TypeTest;
import de.unkrig.zz.find.Find.WritabilityTest;

/**
 * Parses the predicate syntax of the UNIX FIND command line utility.
 */
public
class Parser {

    static { AssertionUtil.enableAssertionsForThisClass(); }

    /**
     * The token types known to this scanner.
     *
     * @see AbstractScanner#AbstractScanner()
     */
    enum TokenType { LITERAL }

    private final AbstractParser<TokenType> parser;
    private final OutputStream              outOS;

    private boolean hadAction;

    /**
     * @param producer The source of tokens to parse
     * @param out      Where the {@link CatAction} writes its output
     */
    public <EX extends Throwable>
    Parser(final ProducerWhichThrows<String, ? extends EX> producer, OutputStream out) {

        this.parser = new AbstractParser<TokenType>(new ProducerWhichThrows<Token<TokenType>, ScanException>() {

            @Override @Nullable public Token<TokenType>
            produce() throws ScanException {

                String text;
                try {
                    text = producer.produce();
                } catch (Error e) {     // SUPPRESS CHECKSTYLE IllegalCatch
                    throw e;
                } catch (Throwable e) { // SUPPRESS CHECKSTYLE IllegalCatch
                    throw ExceptionUtil.wrap(null, e, ScanException.class);
                }

                if (text == null) return null;

                return new Token<TokenType>(TokenType.LITERAL, text);
            }
        });

        this.outOS = out;
    }

    /**
     * <pre>
     * expression := [ or-expression ]
     * </pre>
     * "No token" means "-print".
     * <p>
     * An expression without any actions is silently converted to "<i>expr</i> &amp;&amp; -print".
     * <p>
     * Example:
     * <pre>
     *    -name "*.java" -o ( -name "*foo*" ! -name "*foobar" )
     * </pre>
     */
    public Expression
    parse() throws ParseException {

        if (this.parser.peek() == null) return new PrintAction();

        final Expression result = this.parseComma();
        this.parser.eoi();

        return this.hadAction ? result : new AndTest(result, new PrintAction());
    }

    /**
     * <pre>
     * comma-expression := or-expression [ ',' comma-expression ]
     * </pre>
     */
    private Expression
    parseComma() throws ParseException {
        Expression lhs = this.parseOr();
        return this.parser.peekRead(",") ? new CommaTest(lhs, this.parseComma()) : lhs;
    }

    /**
     * <pre>
     * or-expression := and-expression [ ( '-o' | '-or' | '||' ) or-expression ]
     * </pre>
     */
    private Expression
    parseOr() throws ParseException {
        Expression lhs = this.parseAnd();
        if (this.parser.peekRead("-o", "-or", "||") == -1) return lhs;

        Expression rhs = this.parseOr();
        return new OrTest(lhs, rhs);
    }

    /**
     * <pre>
     * and-expression := primary-expression [ [ '-a' | '-and' | '&&' ] and-expression ]
     * </pre>
     */
    private Expression
    parseAnd() throws ParseException {

        Expression lhs = this.parsePrimary();

        return (
            this.parser.peekRead("-a", "-and", "&&") != -1
            || this.parser.peek("-o", "-or", "||", ")", ",", null) == -1
        ) ? new AndTest(lhs, this.parseAnd()) : lhs;
    }

    /**
     * <pre>
     * primary-expression :=
     *   '(' or-expression ')'
     *   | ( '!' | '-not' ) primary-expression
     *   | '-name' literal
     *   | '-path' literal
     *   | '-type' glob
     *   | '-readable'
     *   | '-writable'
     *   | '-executable'
     *   | '-size' numeric
     *   | '-mtime' numeric
     *   | '-mmin' numeric
     *   | '-print'
     *   | '-echo'
     *   | '-ls'
     *   | '-exec' { literal } ';'
     *   | '-pipe' { literal } ';'
     *   | '-cat'
     *   | '-copy' string
     *   | '-disassemble' boolean boolean file
     *   | '-digest' string
     *   | '-checksum' string
     *   | '-true'
     *   | '-false'
     *   | '-prune'
     *   | '-delete'
     * </pre>
     */
    private Expression
    parsePrimary() throws ParseException {
        switch (this.parser.read(

            // SUPPRESS CHECKSTYLE Wrap:6
            "(",            "!",         "-not",      "-name",       "-path",
            "-type",        "-readable", "-writable", "-executable", "-size",
            "-mtime",       "-mmin",     "-print",    "-echo",       "-printf",
            "-ls",          "-exec",     "-pipe",     "-cat",        "-copy",
            "-disassemble", "-digest",   "-checksum", "-true",       "-false",
            "-prune",       "-delete"
        )) {
        case 0:  // '('
            final Expression result = this.parseComma();
            this.parser.read(")");
            return result;
        case 1:  // '!'
        case 2:  // '-not'
            return new NotExpression(this.parsePrimary());
        case 3:  // '-name' <name-pattern>
            return new NameTest(this.parser.read().text);
        case 4:  // '-path' <path-pattern>
            return new PathTest(this.parser.read().text);
        case 5:  // '-type' glob
            return new TypeTest(this.parser.read().text);
        case 6:  // '-readable'
            return new ReadabilityTest();
        case 7:  // '-writable'
            return new WritabilityTest();
        case 8:  // '-executable'
            return new ExecutabilityTest();
        case 9:  // '-size'
            return new SizeTest(Parser.parseNumericArgument(this.parser.read().text));
        case 10: // '-mtime'
            return new ModificationTimeTest(
                Parser.parseNumericArgument(this.parser.read().text),
                ModificationTimeTest.DAYS
            );
        case 11: // '-mmin'
            return new ModificationTimeTest(
                Parser.parseNumericArgument(this.parser.read().text),
                ModificationTimeTest.MINUTES
            );
        case 12: // '-print'
            this.hadAction = true;
            return new PrintAction();
        case 13: // '-echo'
            this.hadAction = true;
            return new EchoAction(this.parser.read().text);
        case 14: // '-printf'
            this.hadAction = true;
            String       format         = this.parser.read().text;
            List<String> argExpressions = new ArrayList<String>();
            while (!this.parser.peekRead(";")) argExpressions.add(this.parser.read().text);
            return new PrintfAction(format, argExpressions.toArray(new String[argExpressions.size()]));
        case 15: // '-ls'
            this.hadAction = true;
            return new LsAction();
        case 16: // '-exec' word ... ';'
            {
                List<String> command = new ArrayList<String>();
                while (!this.parser.peekRead(";")) command.add(this.parser.read().text);
                this.hadAction = true;
                return new ExecAction(command);
            }
        case 17: // '-pipe' word ... ';'
            {
                List<String> command = new ArrayList<String>();
                while (!this.parser.peekRead(";")) command.add(this.parser.read().text);
                this.hadAction = true;
                return new PipeAction(command, null);
            }
        case 18: // '-cat'
            this.hadAction = true;
            return new CatAction(this.outOS);
        case 19: // '-copy'
            this.hadAction = true;
            boolean append = this.parser.peekRead("-a", "--append") != -1;
            return new CopyAction(new File(this.parser.read().text), append);
        case 20: // "-disassemble"
            this.hadAction = true;
            return new DisassembleAction(
                this.parser.peekRead("-verbose"),        // verbose
                (                                        // sourceDirectory
                    this.parser.peekRead("-sourceDirectory")
                    ? new File(this.parser.read().text)
                    : null
                ),
                this.parser.peekRead("-hideLines"),      // hideLines
                this.parser.peekRead("-hideVars"),       // hideVars
                this.parser.peekRead("-symbolicLabels"), // symbolicLabels
                null                                     // file
            );
        case 21: // "-digest"
            this.hadAction = true;
            return new DigestAction(this.parser.read().text);
        case 22: // "-checksum"
            this.hadAction = true;
            ChecksumType cst;
            try {
                cst = ChecksumAction.ChecksumType.valueOf(this.parser.read().text);
            } catch (IllegalArgumentException iae) {
                throw new IllegalArgumentException(
                    "Invalid checksum type \""
                    + this.parser.read().text
                    + "\"; allowed values are "
                    + Arrays.toString(ChecksumAction.ChecksumType.values())
                );
            }
            return new ChecksumAction(cst);
        case 23: // "-true"
            return Test.TRUE;
        case 24: // "-false"
            return Test.FALSE;
        case 25: // "-prune"
            return new PruneAction();
        case 26: // "-delete"
            return new DeleteAction();
        default:
            throw new IllegalStateException();
        }
    }

    /**
     * <pre>
     * numeric := [ '+' | '-' ] digit { digit } [ 'k' | 'M' | 'G' ]
     * </pre>
     */
    public static Predicate<Long>
    parseNumericArgument(String text) {

        final Mode mode;
        final long n;

        if (text.startsWith("+")) {
            mode = Mode.MORE_THAN;
            text = text.substring(1);
        } else
        if (text.startsWith("-")) {
            mode = Mode.LESS_THAN;
            text = text.substring(1);
        } else
        {
            mode = Mode.EXACTLY;
        }

        long multiplier;
        if (text.endsWith("k")) {
            multiplier = 1024L;
            text       = text.substring(0, text.length() - 1);
        } else
        if (text.endsWith("M")) {
            multiplier = 1024L * 1024L;
            text       = text.substring(0, text.length() - 1);
        } else
        if (text.endsWith("G")) {
            multiplier = 1024L * 1024L * 1024L;
            text       = text.substring(0, text.length() - 1);
        } else
        {
            multiplier = 1L;
        }

        n = multiplier * Long.parseLong(text);

        return new Predicate<Long>() {

            @Override public boolean
            evaluate(Long subject) {
                switch (mode) {
                case EXACTLY:
                    return subject == n;
                case LESS_THAN:
                    return subject < n;
                case MORE_THAN:
                    return subject > n;
                default:
                    throw new IllegalStateException();
                }
            }

            @Override public String
            toString() {
                return (
                    mode == Mode.MORE_THAN ? "> " :
                    mode == Mode.LESS_THAN ? "< " :
                    mode == Mode.EXACTLY ? "== " :
                    "??? "
                ) + n;
            }
        };
    }
    private enum Mode { MORE_THAN, LESS_THAN, EXACTLY }
}
