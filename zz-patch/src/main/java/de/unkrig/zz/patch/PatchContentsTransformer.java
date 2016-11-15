
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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;

import de.unkrig.commons.file.contentstransformation.ContentsTransformer;
import de.unkrig.commons.io.IoUtil;
import de.unkrig.commons.text.StringStream.UnexpectedElementException;
import de.unkrig.zz.patch.diff.DiffParser;
import de.unkrig.zz.patch.diff.DiffParser.Differential;

/**
 * A {@link ContentsTransformer} that applies a patch in NORMAL, CONTEXT or UNIFIED DIFF format.
 */
public
class PatchContentsTransformer extends PatchTextTransformer implements ContentsTransformer {

    private final Charset inputCharset;
    private final Charset outputCharset;

    /**
     * Parses a DIFF document from {@code patchFile}. If it describes more than on {@link Differential}, then all but
     * the first differential are ignored.
     * <p>
     *   The file name information in the differential is ignored.
     * </p>
     *
     * @param condition                      Is evaluated for each hunk, and determines whether or not the hunk is
     *                                       applied
     * @see DiffParser#parse(java.io.Reader)
     * @throws UnexpectedElementException    The {@code patchFile} does not contain a valid DIFF document
     */
    public
    PatchContentsTransformer(
        Charset     inputCharset,
        Charset     outputCharset,
        File        patchFile,
        Charset     patchFileCharset,
        Condition   condition
    ) throws IOException, UnexpectedElementException {

        super(
            DiffParser.parse(patchFile, patchFileCharset).get(0).hunks,
            condition
        );

        this.inputCharset  = inputCharset;
        this.outputCharset = outputCharset;
    }

    /**
     * Applies all patches to the input stream and writes the result into the output stream.
     */
    @Override public void
    transform(String path, InputStream is, OutputStream os) throws IOException {

        if (this.isIdentity()) {
            IoUtil.copy(is, os);
            return;
        }

        this.transform(
            path,
            new InputStreamReader(is, this.inputCharset),
            new OutputStreamWriter(os, this.outputCharset)
        );
    }
}
