
/*
 * de.unkrig.pack - An advanced archive creation utility
 *
 * Copyright (c) 2014, Arno Unkrig
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

package de.unkrig.zz.pack;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.Collator;
import java.util.Comparator;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.StreamingNotSupportedException;
import org.apache.commons.compress.compressors.CompressorException;

import de.unkrig.commons.file.ExceptionHandler;
import de.unkrig.commons.file.contentsprocessing.ContentsProcessings;
import de.unkrig.commons.file.contentsprocessing.ContentsProcessor;
import de.unkrig.commons.file.fileprocessing.FileProcessings;
import de.unkrig.commons.file.fileprocessing.FileProcessor;
import de.unkrig.commons.file.org.apache.commons.compress.archivers.ArchiveFormat;
import de.unkrig.commons.file.org.apache.commons.compress.archivers.ArchiveFormatFactory;
import de.unkrig.commons.file.org.apache.commons.compress.compressors.CompressionFormat;
import de.unkrig.commons.file.org.apache.commons.compress.compressors.CompressionFormatFactory;
import de.unkrig.commons.io.IoUtil;
import de.unkrig.commons.lang.AssertionUtil;
import de.unkrig.commons.lang.protocol.Predicate;
import de.unkrig.commons.lang.protocol.PredicateUtil;
import de.unkrig.commons.lang.protocol.ProducerWhichThrows;
import de.unkrig.commons.nullanalysis.Nullable;
import de.unkrig.commons.util.concurrent.ConcurrentUtil;
import de.unkrig.commons.util.concurrent.SquadExecutor;

/**
 * The central API for the ZZGREP functionality.
 */
public
class Pack {

    static { AssertionUtil.enableAssertionsForThisClass(); }

    // BEGIN CONFIGURATION VARIABLES

    @Nullable private ArchiveFormat       archiveFormat;
    @Nullable private CompressionFormat   compressionFormat;
    @Nullable private ArchiveOutputStream archiveOutputStream;
    private Predicate<? super String>     lookIntoFormat                = PredicateUtil.always();
    @Nullable private Comparator<Object>  directoryMemberNameComparator = Collator.getInstance();
    private ExceptionHandler<IOException> exceptionHandler              = ExceptionHandler.defaultHandler();

    /**
     * Only files/entries which match are packed.
     */
    private Predicate<? super String> pathPredicate = PredicateUtil.always();

    // END CONFIGURATION VARIABLES

    // BEGIN CONFIGURATION SETTERS

    /**
     * Sets the archive format to use; mandatory.
     */
    public void
    setArchiveFormat(ArchiveFormat value) { this.archiveFormat = value; }

    /**
     * Sets the compression format to use; optional.
     */
    public void
    setCompressionFormat(CompressionFormat value) { this.compressionFormat = value; }

    /**
     * Sets the output stream to write to; mandatory.
     */
    public Closeable
    setOutputStream(OutputStream os)
    throws StreamingNotSupportedException, ArchiveException, IOException, CompressorException {

        {
            @Nullable final CompressionFormat cf  = Pack.this.compressionFormat;
            if (cf != null) os = cf.compressorOutputStream(os);
        }

        ArchiveFormat af = Pack.this.archiveFormat;
        if (af == null) throw new ArchiveException("Archive format not specified");

        this.archiveOutputStream = af.archiveOutputStream(os);

        return this.archiveOutputStream;
    }

    /**
     * @param value Is evaluated against <code>"<i>format</i>:<i>path</i>"</code>
     * @see         ArchiveFormatFactory#allFormats()
     * @see         ArchiveFormat#getName()
     * @see         CompressionFormatFactory#allFormats()
     * @see         CompressionFormat#getName()
     */
    public void
    setLookIntoFormat(Predicate<? super String> value) { this.lookIntoFormat = value; }

    /**
     * Only files/entries who's name matches {@code value} are packed.
     */
    public void
    setPathPredicate(Predicate<? super String> value) { this.pathPredicate = value; }

    /**
     * Sets the exception handler.
     */
    public void
    setExceptionHandler(ExceptionHandler<IOException> exceptionHandler) { this.exceptionHandler = exceptionHandler; }

    /**
     * Sets the 'directory member name comparator'.
     *
     * @see FileProcessings#directoryProcessor(Predicate, FileProcessor, Comparator, FileProcessor,
     *      de.unkrig.commons.file.fileprocessing.FileProcessings.DirectoryCombiner, SquadExecutor, ExceptionHandler)
     */
    public void
    setDirectoryMemberNameComparator(@Nullable Comparator<Object> directoryMemberNameComparator) {
        this.directoryMemberNameComparator = directoryMemberNameComparator;
    }

    // END CONFIGURATION SETTERS

    /**
     * @return A {@link ContentsProcessor} which executes the search and prints results to STDOUT
     */
    public ContentsProcessor<Void>
    contentsProcessor() {

        return new ContentsProcessor<Void>() {

            @Override @Nullable public Void
            process(
                String                                                            name,
                final InputStream                                                 is,
                long                                                              size,
                long                                                              crc32,
                ProducerWhichThrows<? extends InputStream, ? extends IOException> opener
            ) throws IOException {

                if (!Pack.this.pathPredicate.evaluate(name)) return null;

                AssertionUtil.notNull(Pack.this.archiveFormat).writeEntry(
                    AssertionUtil.notNull(Pack.this.archiveOutputStream),
                    name,
                    IoUtil.copyFrom(is)
                );
                return null;
            }
        };
    }

    /**
     * @return A {@link FileProcessor} which executes the search and prints results to STDOUT
     */
    public FileProcessor<Void>
    fileProcessor(boolean lookIntoDirectories) {

        FileProcessor<Void> fp = FileProcessings.recursiveCompressedAndArchiveFileProcessor(
            this.lookIntoFormat,                            // lookIntoFormat
            this.pathPredicate,                             // pathPredicate
            ContentsProcessings.<Void>nopArchiveCombiner(), // archiveEntryCombiner
            this.contentsProcessor(),                       // contentsProcessor
            this.exceptionHandler                           // exceptionHandler
        );

        // Honor the 'lookIntoDirectories' flag.
        if (lookIntoDirectories) {
            fp = FileProcessings.directoryTreeProcessor(
                this.pathPredicate,                                                  // pathPredicate
                fp,                                                                  // regularFileProcessor
                this.directoryMemberNameComparator,                                  // directoryMemberNameComparator
                FileProcessings.<Void>nopDirectoryCombiner(),                        // directoryCombiner
                new SquadExecutor<Void>(ConcurrentUtil.SEQUENTIAL_EXECUTOR_SERVICE), // squadExecutor
                this.exceptionHandler                                                // exceptionHandler
            );
        }

        return fp;
    }
}
