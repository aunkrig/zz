
/*
 * de.unkrig.ant-contrib - Some contributions to APACHE ANT
 *
 * Copyright (c) 2015, Arno Unkrig
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
import java.io.IOException;
import java.io.PipedReader;
import java.io.PipedWriter;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.List;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.ProjectComponent;
import org.apache.tools.ant.filters.ChainableReader;

import de.unkrig.commons.file.contentstransformation.TextTransformer;
import de.unkrig.commons.lang.ThreadUtil;
import de.unkrig.commons.lang.protocol.Mappings;
import de.unkrig.commons.lang.protocol.RunnableWhichThrows;
import de.unkrig.commons.nullanalysis.NotNull;
import de.unkrig.commons.nullanalysis.NotNullByDefault;
import de.unkrig.commons.nullanalysis.Nullable;
import de.unkrig.commons.text.StringStream.UnexpectedElementException;
import de.unkrig.commons.text.expression.EvaluationException;
import de.unkrig.commons.text.expression.Expression;
import de.unkrig.commons.text.expression.ExpressionEvaluator;
import de.unkrig.commons.text.parser.ParseException;
import de.unkrig.zz.patch.diff.DiffParser;
import de.unkrig.zz.patch.diff.DiffParser.Hunk;

/**
 * An ANT filter which modifies the data by applying a patch file.
 */
@NotNullByDefault(false) public
class AntFilter extends ProjectComponent implements ChainableReader {

    @Nullable private File                 patchFile;
    private Charset                        patchFileCharset = Charset.defaultCharset();
    private PatchTextTransformer.Condition condition        = PatchTextTransformer.Condition.ALWAYS;

    // ---------------- ANT attribute setters. ----------------

    /**
     * The file that contains the DIFF document that is to be applied to the input.
     * <p>
     *   A DIFF document generally contains differentials for one or more files. Each differential comprises a sequence
     *   of "hunks" in "traditional diff", "context diff" or "unified diff" format.
     * </p>
     * <p>
     *   If the DIFF document describes more than on differential, then all but the first differential are ignored.
     *   The file name information in the differential is also ignored.
     * </p>
     */
    public void setPatchFile(File value) { this.patchFile = value; }

    /**
     * The encoding of the patch file; defaults to the platform default encoding.
     */
    public void setPatchFileEncoding(String value) { this.patchFileCharset = Charset.forName(value); }

    /**
     * Configures a condition that must evaluate to {@code true} before each DIFF hunk is applied.
     */
    public void
    setCondition(String condition) throws ParseException {

        final Expression
        expression = new ExpressionEvaluator("hunks", "hunkIndex", "hunk", "lineNumber").parse(condition);

        this.condition = new PatchTextTransformer.Condition() {

            @Override public boolean
            evaluate(
                @NotNull String     path,
                @NotNull List<Hunk> hunks,
                int                 hunkIndex,
                @NotNull Hunk       hunk,
                int                 lineNumber
            ) {

                try {

                    return ExpressionEvaluator.toBoolean(
                        expression.evaluate(Mappings.<String, Object>mapping(
                            "hunks",      hunks,
                            "hunkIndex",  hunkIndex,
                            "lineNumber", lineNumber
                        ))
                    );
                } catch (EvaluationException ee) {
                    throw new RuntimeException(ee);
                }
            }
        };
    }

    // ---------------- Implementation of ChainableReader ----------------

    @Override public Reader
    chain(final Reader reader) {

        File patchFile = this.patchFile;
        if (patchFile == null) throw new BuildException("Attribute 'patchFile' must be set");

        // Parse the patch file.
        List<Hunk> hunks;
        try {
            hunks = DiffParser.parse(patchFile, this.patchFileCharset).get(0).hunks;
        } catch (UnexpectedElementException uee) {
            throw new BuildException(uee);
        } catch (IOException ioe) {
            throw new BuildException(ioe);
        }

        // Create a pipe.
        final PipedWriter pipedWriter = new PipedWriter();
        PipedReader       pipedReader;
        try {
            pipedReader = new PipedReader(pipedWriter);
        } catch (IOException ioe) {
            throw new BuildException(ioe);
        }

        // Set up the 'PatchTextTransformer'.
        final TextTransformer textTransformer = new PatchTextTransformer(hunks, this.condition);

        // Execute the transformer in the background, catching its output with the previously created pipe.
        ThreadUtil.runInBackground(new RunnableWhichThrows<IOException>() {

            @Override public void
            run() throws IOException {
                textTransformer.transform("", reader, pipedWriter);
                pipedWriter.close();
                reader.close();
            }
        }, "ant-patch-filter");

        return pipedReader;
    }

    // ---------------------------------- IMPLEMENTATION -----------------------------------
}

