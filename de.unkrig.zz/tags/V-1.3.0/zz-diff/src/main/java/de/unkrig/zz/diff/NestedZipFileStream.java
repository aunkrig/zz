
/*
 * de.unkrig.diff - An advanced version of the UNIX DIFF utility
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

package de.unkrig.zz.diff;

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import de.unkrig.commons.lang.ExceptionUtil;
import de.unkrig.commons.nullanalysis.Nullable;

/**
 * An enhanced version of {@link ZipInputStream} and {@link ZipFile}: Supports random access even to
 * <i>nested</i> entries of a ZIP file.
 * <p>
 *   Random access to nested entries is generally problematic, because the top-level ZIP entries must be re-read for
 *   each access. This class attempts to optimize the preformance by keeping book on the chain of previously opened
 *   entries.
 * </p>
 * <p>
 *   Example: The previously read entry was "a!b!c!d!e". If the next entry to read is "a!b!c!x!y!z", then this class
 *   searched the entries <i>after</i> "d", and if it finds an entry "x", then it reads its sub-sub entry "y!z", which
 *   is much faster than reading the subsub...entry "b!c!x!y!z" of top-level entry "a".
 * </p>
 */
public
class NestedZipFileStream extends FilterInputStream {

    private static final Logger LOGGER = Logger.getAnonymousLogger();
    static { NestedZipFileStream.LOGGER.setParent(Logger.getLogger(NestedZipFileStream.class.getName())); }

    private final ZipFile    zipFile;
    private ZipInputStream[] currentNestedZips  = new ZipInputStream[0];
    private String[]         currentNestedNames = new String[0];

    public
    NestedZipFileStream(File file) throws IOException {
        super(null);
        try {
            this.zipFile = new ZipFile(file);
        } catch (IOException ioe) {
            throw ExceptionUtil.wrap("Opening '" + file + "'", ioe);
        }
    }

    @Override public void
    close() throws IOException {
        for (int j = 0; j < this.currentNestedZips.length; j++) {
            if (this.currentNestedZips[j] != null) {
                this.currentNestedZips[j].close();
                this.currentNestedZips[j] = null;
            }
        }
        this.zipFile.close();
    }

    /**
     * Returns the {@link ZipEntry} designated by {@code names} and positions the stream to read the contents of that
     * entry.
     *
     * @param names Entries 0...n-2 designate entries (like 'dir/dir/my.zip') for nested ZIP archives; entry n-1
     *              designates the entry that is to be read (like 'dir/dir/my.file')
     * @return      {@code null} iff a entry with the given names cannot be found
     */
    @Nullable public ZipEntry
    getEntry(String[] names) throws IOException {
        if (NestedZipFileStream.LOGGER.isLoggable(Level.FINER)) {
            NestedZipFileStream.LOGGER.log(Level.FINER, "ENTRY names={0}", Arrays.asList(names));
        }

        if (names.length == 0) throw new IllegalArgumentException();

        // Attempt to re-use the currently open zip input streams.
        REUSE: {
            int i = 0;
            for (; (
                i < names.length - 1
                && i < this.currentNestedNames.length
                && names[i].equals(this.currentNestedNames[i])
            ); i++);

            if (i == 0) {
                for (int j = this.currentNestedZips.length - 1; j >= 0; j--) {
                    if (this.currentNestedZips[j] != null) {
                        this.currentNestedZips[j].close();
                        this.currentNestedZips[j] = null;
                    }
                }
                break REUSE;
            }

            if (NestedZipFileStream.LOGGER.isLoggable(Level.FINER)) {
                NestedZipFileStream.LOGGER.log(Level.FINER, "Attempt reuse from level {0}", i);
            }

            {
                ZipInputStream[] tmp = new ZipInputStream[names.length];
                System.arraycopy(this.currentNestedZips, 0, tmp, 0, i);
                for (int j = this.currentNestedZips.length - 1; j >= i; j--) {
                    if (this.currentNestedZips[j] != null) this.currentNestedZips[j].close();
                }
                this.currentNestedZips = tmp;
            }
            {
                String[] tmp = new String[names.length];
                System.arraycopy(this.currentNestedNames, 0, tmp, 0, i);
                this.currentNestedNames = tmp;
            }
            {
                for (;;) {
                    ZipEntry ze = this.currentNestedZips[i - 1].getNextEntry();
                    if (ze == null) {
                        if (NestedZipFileStream.LOGGER.isLoggable(Level.FINER)) {
                            NestedZipFileStream.LOGGER.log(Level.FINER, "Attempted reuse failed on level {0}", i);
                        }
                        for (int j = i - 1; j >= 0; j--) {
                            if (this.currentNestedZips[j] != null) {
                                this.currentNestedZips[j].close();
                                this.currentNestedZips[j] = null;
                            }
                        }
                        break REUSE;
                    }
                    if (ze.getName().equals(names[i])) {
                        if (i == names.length - 1) {
                            this.in = this.currentNestedZips[i - 1];
                            if (NestedZipFileStream.LOGGER.isLoggable(Level.FINER)) {
                                NestedZipFileStream.LOGGER.finer("Attempted reuse successful");
                            }
                            return ze;
                        }
                        this.currentNestedZips[i]  = new ZipInputStream(this.currentNestedZips[i - 1]);
                        this.currentNestedNames[i] = ze.getName();
                        if (NestedZipFileStream.LOGGER.isLoggable(Level.FINER)) {
                            NestedZipFileStream.LOGGER.log(Level.FINER, "Reusing level {0}", i);
                        }
                        break;
                    }
                }
            }
            for (i++; i < names.length; i++) {
                for (;;) {
                    ZipEntry ze = this.currentNestedZips[i - 1].getNextEntry();
                    if (ze == null) {
                        return null;
                    }
                    if (ze.getName().equals(names[i])) {
                        if (i == names.length - 1) {
                            this.in = this.currentNestedZips[i - 1];
                            return ze;
                        }
                        this.currentNestedZips[i]  = new ZipInputStream(this.currentNestedZips[i - 1]);
                        this.currentNestedNames[i] = ze.getName();
                        break;
                    }
                }
            }
        }

        // Could not reuse the ZipInputStreams that were left from the last invocation.
        if (NestedZipFileStream.LOGGER.isLoggable(Level.FINE)) {
            NestedZipFileStream.LOGGER.log(Level.FINE, "Processing ''{0}''", this.zipFile.getName() + "!" + names[0]);
        }
        this.currentNestedZips  = new ZipInputStream[names.length - 1];
        this.currentNestedNames = new String[names.length - 1];

        {
            ZipEntry ze = this.zipFile.getEntry(names[0]);
            if (ze == null) {
                return null;
            }
            this.in = this.zipFile.getInputStream(ze);
            if (names.length == 1) return ze;
            this.currentNestedNames[0] = names[0];
            this.currentNestedZips[0]  = new ZipInputStream(this.in);
        }

        for (int idx = 1;; ++idx) {
            for (;;) {
                ZipEntry ze = this.currentNestedZips[idx - 1].getNextEntry();
                if (ze == null) {
                    return null;
                }
                if (ze.getName().equals(names[idx])) {
                    this.in = this.currentNestedZips[idx - 1];
                    if (idx == names.length - 1) {
                        return ze;
                    }
                    this.currentNestedNames[idx] = ze.getName();
                    this.currentNestedZips[idx]  = new ZipInputStream(this.currentNestedZips[idx - 1]);
                    break;
                }
            }
        }
    }
}
