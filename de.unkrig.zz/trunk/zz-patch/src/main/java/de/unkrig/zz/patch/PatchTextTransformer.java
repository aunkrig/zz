
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.unkrig.commons.file.contentstransformation.ContentsTransformer;
import de.unkrig.commons.file.contentstransformation.TextTransformer;
import de.unkrig.commons.io.IoUtil;
import de.unkrig.commons.io.LineUtil;
import de.unkrig.commons.lang.protocol.ProducerWhichThrows;
import de.unkrig.commons.text.StringStream.UnexpectedElementException;
import de.unkrig.zz.patch.diff.DiffException;
import de.unkrig.zz.patch.diff.DiffParser;
import de.unkrig.zz.patch.diff.DiffParser.Differential;
import de.unkrig.zz.patch.diff.DiffParser.Hunk;
import de.unkrig.zz.patch.diff.DiffParser.LineChange;

/**
 * A {@link ContentsTransformer} that applies a patch in NORMAL, CONTEXT or UNIFIED DIFF format.
 */
public
class PatchTextTransformer implements TextTransformer {

    private static final Logger LOGGER = Logger.getLogger(PatchTextTransformer.class.getName());

    private final List<Hunk> hunks;
    private final Condition  condition;

    /**
     * Parses a DIFF document from {@code patches}. If it describes more than on {@link Differential}, then all but
     * the first differential is ignored.
     * <p>
     * The file name information in the differential is ignored.
     *
     * @param condition                      Is evaluated for each hunk, and determines whether or not the hunk is
     *                                       applied
     * @see DiffParser#parse(java.io.Reader)
     * @throws UnexpectedElementException    The {@code patchFile} does not contain a valid DIFF document
     */
    public
    PatchTextTransformer(List<Hunk> hunks, Condition condition) {
        this.hunks     = hunks;
        this.condition = condition;
    }

    /**
     * @see Condition#evaluate(String, List, int, DiffParser.Hunk, int)
     */
    public
    interface Condition {

        /** Constantly evaluates to {@code true}. */
        Condition
        ALWAYS = new Condition() {

            @Override public boolean
            evaluate(String path, List<Hunk> hunks, int hunkIndex, Hunk hunk, int lineNumber) { return true; }
        };

        /**
         * @param path       The 'path' of the file or ZIP entry being patched
         * @param hunks      The hunks being applied
         * @param hunkIndex  The index of the current hunk
         * @param hunk       The current hunk
         * @param lineNumber The line number where the hunk starts
         * @return           Whether the current hunk should be applied, see {@link
         *                   PatchTextTransformer#PatchTextTransformer(List, Condition)}
         */
        boolean evaluate(String path, List<Hunk> hunks, int hunkIndex, Hunk hunk, int lineNumber);
    }

    public boolean
    isIdentity() { return this.hunks.isEmpty(); }

    /**
     * Applies all patches to the {@code reader} and writes the result to the {@code writer}.
     */
    @Override
    public void
    transform(String path, Reader reader, Writer writer) throws IOException {

        if (this.hunks.isEmpty()) {
            IoUtil.copy(reader, writer);
            return;
        }

        PatchTextTransformer.LOGGER.log(
            Level.CONFIG,
            "{0}: Patch {1} {1,choice,1#hunk|1<hunks}",
            new Object[] { path, this.hunks.size() }
        );

        ProducerWhichThrows<String, ? extends IOException>
        lineReader = LineUtil.readLineWithSeparator(new BufferedReader(reader));

        // Process all changes.
        int i = 0;
        for (int hunkIndex = 0; hunkIndex < this.hunks.size(); hunkIndex++) {
            Hunk hunk = this.hunks.get(hunkIndex);

            if (!this.condition.evaluate(path, this.hunks, hunkIndex, hunk, hunk.from1 + 1)) continue;

            if (i > hunk.from1) throw new DiffException("Changes overlap");

            // Copy lines between hunks.
            for (; i < hunk.from1; i++) {
                String line = lineReader.produce();
                if (line == null) throw new DiffException("Hunk begins beyond end-of-file");
                writer.write(line);
            }

            // Process hunk lines.
            for (LineChange lc : hunk.lineChanges) {
                switch (lc.mode) {

                case CONTEXT:
                    {
                        String line = lineReader.produce();
                        if (line == null) throw new DiffException("Context line beyond end-of-file");
                        if (!lc.text.equals(line)) {
                            throw new DiffException(
                                "Context mismatch; expected \""
                                + lc.text
                                + "\", but was \""
                                + line
                                + "\""
                            );
                        }
                    }
                    writer.write(lc.text);
                    i++;
                    break;

                case ADDED:
                    writer.write(lc.text);
                    break;

                case DELETED:
                    {
                        String line = lineReader.produce();
                        if (line == null) throw new DiffException("Deleted line beyond end-of-file");
                        if (!lc.text.equals(line)) {
                            throw new DiffException(
                                "Deleted line mismatch; expected \""
                                + lc.text
                                + "\", but was \""
                                + line
                                + "\""
                            );
                        }
                    }
                    i++;
                    break;

                default:
                    throw new AssertionError();
                }
            }

            if (hunk.from1 == i) {
                PatchTextTransformer.LOGGER.log(
                    Level.CONFIG,
                    "{0}: Patching after line {1}",
                    new Object[] { path, i }
                );
            } else
            if (hunk.from1 == i - 1) {
                PatchTextTransformer.LOGGER.log(
                    Level.CONFIG,
                    "{0}: Patching line {1}",
                    new Object[] { path, i }
                );
            } else
            {
                PatchTextTransformer.LOGGER.log(
                    Level.CONFIG,
                    "{0}: Patching lines {1}...{2}",
                    new Object[] { path, hunk.from1 + 1, i }
                );
            }
        }

        // Copy lines after last hunk.
        for (;;) {
            String line = lineReader.produce();
            if (line == null) break;
            writer.write(line);
        }

        writer.flush();

        PatchTextTransformer.LOGGER.log(
            Level.FINE,
            "{0}: Patched {1,choice,0#{1} hunks|1#{1} hunk|1<{1} hunks}",
            new Object[] { path, this.hunks.size() }
        );
    }
}
