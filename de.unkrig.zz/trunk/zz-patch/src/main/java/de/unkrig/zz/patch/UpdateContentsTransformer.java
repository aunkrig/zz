
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

package de.unkrig.zz.patch;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.unkrig.commons.file.contentstransformation.ContentsTransformer;
import de.unkrig.commons.io.IoUtil;
import de.unkrig.commons.text.pattern.Glob;

/**
 * A {@link ContentsTransformer} that replaces the contents with that of a given "update file".
 */
public
class UpdateContentsTransformer
implements ContentsTransformer {

    private static final Logger LOGGER = Logger.getLogger(UpdateContentsTransformer.class.getName());

    private final Glob updateFile;

    public
    UpdateContentsTransformer(Glob updateFile) { this.updateFile = updateFile; }

    @Override public void
    transform(String path, InputStream is, OutputStream os) throws IOException {

        String updateFilePath = this.updateFile.replace(path);
        if (updateFilePath == null) {

            IoUtil.copy(is, os);
        } else {

            UpdateContentsTransformer.LOGGER.log(
                Level.CONFIG,
                "Updating ''{0}'' from ''{1}''",
                new Object[] { path, updateFilePath }
            );
            IoUtil.copy(new FileInputStream(new File(updateFilePath)), true, os, false);
        }
    }

    @Override public String
    toString() { return "UPDATE with '" + this.updateFile + "'"; }
}
