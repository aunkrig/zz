
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
import java.io.OutputStream;

import de.unkrig.commons.io.ByteFilter;
import de.unkrig.commons.nullanalysis.Nullable;
import de.unkrig.jdisasm.Disassembler;

/**
 * Reads bytes in Java <a href="http://java.sun.com/docs/books/jvms/second_edition/html/ClassFile.doc.html">"class file
 * format"</a> and disassembles it to a human-readable form.
 *
 * Requires the "de.unkrig.jdisasm" utility, available on
 * {@code https://github.com/aunkrig/jdisasm}.
 *
 * Requires the "de.unkrig.commons.util" and "de.unkrig.commons.io" libraries, available on
 * {@code http://commons.unkrig.de}.
 */
public
class DisassemblerByteFilter implements ByteFilter<Void> {

    private boolean        verbose;
    @Nullable private File sourceDirectory;
    private boolean        hideLines;
    private boolean        hideVars;
    private boolean        symbolicLabels;

    @Override @Nullable public Void
    run(InputStream in, OutputStream out) throws IOException {
        Disassembler disassembler = new Disassembler();

        disassembler.setOut(out);
        
        disassembler.setVerbose(this.verbose);
        disassembler.setSourceDirectory(this.sourceDirectory);
        disassembler.setHideLines(this.hideLines);
        disassembler.setHideVars(this.hideVars);
        disassembler.setSymbolicLabels(this.symbolicLabels);

        disassembler.disasm(in);

        return null;
    }

    /**
     * @param value Whether to include a constant pool dump, constant pool indexes, and hex dumps of all attributes
     *              in the disassembly output
     */
    public void
    setVerbose(boolean value) { this.verbose = value; }

    /**
     * @param value Where to look for source files when disassembling .class files; {@code null} disables source file
     *              loading; source file loading is disabled by default
     */
    public void
    setSourceDirectory(@Nullable File value) { this.sourceDirectory = value; }

    /**
     * @param value Whether source line numbers are suppressed in the disassembly (defaults to {@code false})
     */
    public void
    setHideLines(boolean value) { this.hideLines = value; }

    /**
     * @param value Whether local variable names are suppressed in the disassembly (defaults to {@code false})
     */
    public void
    setHideVars(boolean value) { this.hideVars = value; }

    /**
     * @param value Whether to use numeric labels ('#123') or symbolic labels /'L12') in the bytecode disassembly
     */
    public void
    setSymbolicLabels(boolean value) { this.symbolicLabels = value; }
}
