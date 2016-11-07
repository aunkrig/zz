
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

package de.unkrig.zz.grep;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import de.unkrig.commons.io.ByteFilter;
import de.unkrig.commons.nullanalysis.Nullable;
import de.unkrig.jdisasm.Disassembler;

/**
 * Reads bytes in Java <a href="http://java.sun.com/docs/books/jvms/second_edition/html/ClassFile.doc.html">"class file
 * format"</a> and disassembles it to a human-readable form.
 *
 * Requires the "de.unkrig.jdisasm" utility, available on
 * {@code https://svn.codehaus.org/janino/trunk}.
 *
 * Requires the "de.unkrig.commons.util" and "de.unkrig.commons.io" libraries, available on
 * {@code https://loggifier.svn.sourceforge.net/svnroot/loggifier/trunk}.
 */
public
class DisassemblerByteFilter implements ByteFilter<Void> {

    private boolean hideLines;
    private boolean hideVars;

    @Override @Nullable public Void
    run(InputStream in, OutputStream out) throws IOException {
        Disassembler disassembler = new Disassembler();

        disassembler.setHideLines(this.hideLines);
        disassembler.setHideVars(this.hideVars);
        disassembler.setOut(out);

        disassembler.disasm(in);

        return null;
    }

    /**
     * @param value Whether source line numbers are suppressed in the disassembly
     */
    public void
    setHideLines(boolean value) { this.hideLines = value; }

    /**
     * @param value Whether local variable names are suppressed in the disassembly
     */
    public void
    setHideVars(boolean value) { this.hideVars = value; }
}