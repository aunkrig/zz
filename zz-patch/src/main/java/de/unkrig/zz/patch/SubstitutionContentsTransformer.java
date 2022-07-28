
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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.unkrig.commons.file.contentstransformation.ContentsTransformer;
import de.unkrig.commons.lang.protocol.Function;
import de.unkrig.commons.lang.protocol.FunctionWhichThrows;
import de.unkrig.commons.lang.protocol.Predicate;
import de.unkrig.commons.lang.protocol.PredicateUtil;
import de.unkrig.commons.nullanalysis.Nullable;
import de.unkrig.commons.text.parser.ParseException;
import de.unkrig.commons.text.pattern.ExpressionMatchReplacer;
import de.unkrig.commons.text.pattern.PatternUtil;

/**
 * A {@link ContentsTransformer} that replaces regex matches. The pattern search is stream-oriented, not line-oriented,
 * i.e. matches are found even across line boundaries.
 */
public
class SubstitutionContentsTransformer implements ContentsTransformer {

    public enum Mode {

        /** @see PatternUtil#replacementStringMatchReplacer(String) */
        REPLACEMENT_STRING,

        /** @see PatternUtil#constantMatchReplacer(String) */
        CONSTANT,

        /** @see ExpressionMatchReplacer#parse(String) */
        EXPRESSION,
    }

    private static final Logger LOGGER = Logger.getLogger(SubstitutionContentsTransformer.class.getName());

    private final Charset                                                              inputCharset;
    private final Charset                                                              outputCharset;
    private final Pattern                                                              pattern;
    private       FunctionWhichThrows<MatchResult, String, ? extends RuntimeException> replacer;
    private final Condition                                                            condition;

    private int initialBufferCapacity = 8192;

    /**
     * Replaces all matches of the <var>regex</var> according to the <var>replacementString</var>.
     *
     * @param condition                Is checked for each match, and determines whether or not the match is replaced
     * @see Matcher#replaceAll(String) For the format of the <var>replacementString</var>
     */
    public
    SubstitutionContentsTransformer(
        Charset     inputCharset,
        Charset     outputCharset,
        Pattern     pattern,
        String      replacementString,
        Condition   condition
    ) {
        this(
            inputCharset,
            outputCharset,
            pattern,
            PatternUtil.<RuntimeException>replacementStringMatchReplacer(replacementString), // replacer
            condition
        );
    }

    /**
     * Replaces all matches of the <var>regex</var> according to the <var>replacementMode</var> and the
     * <var>replacement</var>.
     *
     * @param condition                Is checked for each match, and determines whether or not the match is replaced
     * @throws ParseException          <var>mode</var> was {@link Mode#EXPRESSION}, and <var>s</var> contained
     *                                 syntax errors
     * @see Matcher#replaceAll(String) For the format of the <var>replacementString</var>
     */
    public
    SubstitutionContentsTransformer(
        Charset     inputCharset,
        Charset     outputCharset,
        Pattern     pattern,
        Mode        replacementMode,
        String      replacement,
        Condition   condition
    ) throws ParseException {
        this(
            inputCharset,
            outputCharset,
            pattern,
            SubstitutionContentsTransformer.makeReplacer(replacementMode, replacement), // replacer
            condition
        );
    }

    /**
     * The given <var>replacementListener</var> will be {@link Function#call(Object)}ed for each replacement, and has
     * the chance to modify the replacement string.
     */
    public void
    addReplacementListener(Function<String, String> replacementListener) {

        FunctionWhichThrows<MatchResult, String, ? extends RuntimeException> delegate = this.replacer;
        this.replacer = new Function<MatchResult, String>() {

            @Override @Nullable public String
            call(@Nullable MatchResult matchResult) {
                return replacementListener.call(delegate.call(matchResult));
            }

            @Override
            public String toString() { return delegate.toString(); }
        };
    }

    /**
     * Replaces all matches of the <var>regex</var> according to the <var>replacementString</var>.
     *
     * @param condition                Is checked for each match, and determines whether or not the match is replaced
     * @see Matcher#replaceAll(String) For the format of the <var>replacementString</var>
     */
    public
    SubstitutionContentsTransformer(
        Charset                                                              inputCharset,
        Charset                                                              outputCharset,
        Pattern                                                              pattern,
        FunctionWhichThrows<MatchResult, String, ? extends RuntimeException> replacer,
        Condition                                                            condition
    ) {
        this.inputCharset  = inputCharset;
        this.outputCharset = outputCharset;
        this.pattern       = pattern;
        this.replacer      = replacer;
        this.condition     = condition;
    }

    /**
     * @param initialBufferCapacity See {@link PatternUtil#replaceSome(java.io.Reader, Pattern, FunctionWhichThrows,
     *                              Appendable, int)}
     */
    public void
    setInitialBufferCapacity(int initialBufferCapacity) { this.initialBufferCapacity = initialBufferCapacity; }

    @Override public String
    toString() { return "(" + this.pattern + " => " + this.replacer + " iff " + this.condition + ")"; }

    /**
     * @see #evaluate(String, CharSequence, int)
     */
    public
    interface Condition {

        /**
         * @param path       The 'path' of the file or ZIP entry that contains the match
         * @param match      The matching text
         * @param occurrence The index of the occurrence within the document, starting at zero
         * @return           Whether the matching text should be replaced, see {@link
         *                   SubstitutionContentsTransformer#SubstitutionContentsTransformer(Charset, Charset, Pattern,
         *                   Mode, String, Condition)}
         */
        boolean evaluate(String path, CharSequence match, int occurrence);

        /**
         * A {@link Condition} that is always {@code true}.
         */
        Condition ALWAYS = new Condition() {
            @Override public boolean evaluate(String name, CharSequence match, int occurrence) { return true;     }
            @Override public String  toString()                                                { return "ALWAYS"; }
        };

        /**
         * A {@link Condition} that is always {@code false}.
         */
        Condition NEVER = new Condition() {
            @Override public boolean evaluate(String name, CharSequence match, int occurrence) { return false;   }
            @Override public String  toString()                                                { return "NEVER"; }
        };
    }

    @Override public void
    transform(final String path, InputStream is, OutputStream os) throws IOException {

        SubstitutionContentsTransformer.LOGGER.log(
            Level.FINE,
            "Substituting matches of ''{0}'' with ''{1}'' in ''{2}'' iff ''{3}''",
            new Object[] { this.pattern, this.replacer, path, SubstitutionContentsTransformer.this.condition }
        );

        FunctionWhichThrows<MatchResult, String, ? extends RuntimeException>
        r = this.replacer;

        // Honor the "replacement condition".
        r = SubstitutionContentsTransformer.conditional(path, r, this.condition);

        Writer out = new OutputStreamWriter(os, this.outputCharset);

        int count = PatternUtil.replaceSome(
            new InputStreamReader(is, this.inputCharset), // in
            this.pattern,                                 // pattern
            r,                                            // matchReplacer
            out,                                          // out
            this.initialBufferCapacity                    // bufferCapacity
        );

        if (count == 0) {
            SubstitutionContentsTransformer.LOGGER.log(
                Level.FINE,
                "No matches of \"{0}\" in \"{1}\" to replace with \"{2}\"",
                new Object[] { this.pattern, path, this.replacer }
            );
        } else {
            SubstitutionContentsTransformer.LOGGER.log(
                Level.CONFIG,
                "{0} {0,choice,1#match|1<matches} of \"{1}\" in \"{2}\" {0,choice,1#was|1<were} replaced with \"{3}\"",
                new Object[] { count, this.pattern, path, this.replacer }
            );
        }

        out.flush();
    }

    private static FunctionWhichThrows<MatchResult, String, ? extends RuntimeException>
    makeReplacer(Mode mode, String s) throws ParseException {

        switch (mode) {

        case REPLACEMENT_STRING:
            Predicate<String> ppp = PredicateUtil.<String>equal("m");
            return ExpressionMatchReplacer.parseExt(s); // Supports "${...}" for embedded expressions.

        case CONSTANT:
            return PatternUtil.constantMatchReplacer(s);

        case EXPRESSION:
            return ExpressionMatchReplacer.parse(s);

        default:
            throw new AssertionError(mode);
        }
    }

    private static FunctionWhichThrows<MatchResult, String, ? extends RuntimeException>
    conditional(
        final String                                                               path,
        final FunctionWhichThrows<MatchResult, String, ? extends RuntimeException> replacer,
        final Condition                                                            condition
    ) {

        if (condition == SubstitutionContentsTransformer.Condition.ALWAYS) return replacer;

        return new Function<MatchResult, String>() {

            private int occurrence;

            @Override @Nullable public String
            call(@Nullable MatchResult matchResult) {
                assert matchResult != null;

                if (!condition.evaluate(
                    path,                // path
                    matchResult.group(), // match
                    this.occurrence++    // occurrence
                )) return null;

                return replacer.call(matchResult);
            }

            @Override public String
            toString() { return "[if (" + condition + ") then (" + replacer + ")]"; }
        };
    }
}
