
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

package de.unkrig.zz.pack;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.Resource;
import org.apache.tools.ant.types.ResourceCollection;
import org.apache.tools.ant.types.resources.FileProvider;

import de.unkrig.commons.file.contentsprocessing.ContentsProcessor;
import de.unkrig.commons.file.fileprocessing.FileProcessor;
import de.unkrig.commons.file.org.apache.commons.compress.archivers.ArchiveFormatFactory;
import de.unkrig.commons.file.org.apache.commons.compress.compressors.CompressionFormatFactory;
import de.unkrig.commons.lang.AssertionUtil;
import de.unkrig.commons.lang.protocol.ProducerWhichThrows;
import de.unkrig.commons.nullanalysis.Nullable;
import de.unkrig.commons.text.pattern.Glob;
import de.unkrig.commons.text.pattern.Pattern2;

/**
 * The 'de.unkrig.zz.pack' ANT task; see 'src/de/unkrig/zz/antlib.xml'.
 */
public
class AntTask extends Task {

    private static final Logger LOGGER = Logger.getLogger(Task.class.getName());

    private final Pack pack = new Pack();

    @Nullable private File                 archiveFile;
    @Nullable private File                 file;
    private final List<ResourceCollection> resourceCollections = new ArrayList<ResourceCollection>();


    // BEGIN CONFIGURATION SETTERS

    /**
     * The archive file to create.
     *
     * @ant.mandatory
     */
    public void
    setArchiveFile(File archiveFile) { this.archiveFile = archiveFile; }

    /**
     * The archive format to use.
     *
     * @ant.mandatory
     */
    public void
    setArchiveFormat(String archiveFormat)
    throws ArchiveException { this.pack.setArchiveFormat(ArchiveFormatFactory.forFormatName(archiveFormat)); }

    /**
     * The compression format used to compress the archive. The default is to <em>not</em> compress the archive.
     */
    public void
    setCompressionFormat(String compressionFormat) throws CompressorException {
        this.pack.setCompressionFormat(CompressionFormatFactory.forFormatName(compressionFormat));
    }

    /**
     * One more file to put into the archive.
     */
    public void
    setFile(File file) { this.file = file; }

    /**
     * Into which archive formats and compression formats to look when processing input files. The default is to look
     * into <em>any</em> recognized format.
     *
     * @ant.valueExplanation <var>format-glob</var>:<var>path-glob</var>
     */
    public void
    setLookInto(String glob) {
        this.pack.setLookIntoFormat(Glob.compile(glob, Pattern2.WILDCARD | Glob.INCLUDES_EXCLUDES));
    }

    /**
     * If configured, then only the input files, archive entries and nested archive entries are processed which match
     * the given <var>glob</var>.
     */
    public void
    setName(String glob) {
        this.pack.setNamePredicate(Glob.compile(glob, Pattern2.WILDCARD | Glob.INCLUDES_EXCLUDES));
    }

    /**
     * Another set of files or directory trees to pack into the archive.
     */
    public void
    addConfigured(ResourceCollection value) { this.resourceCollections.add(value); }

    // END CONFIGURATION SETTERS

    /**
     * The ANT task "execute" method.
     *
     * @see Task#execute
     */
    @Override public void
    execute() throws BuildException {

        try {
            this.execute2();
        } catch (BuildException be) {
            throw be;
        } catch (Exception e) {
            throw new BuildException(e);
        }
    }

    private void
    execute2() throws Exception {

        File archiveFile = this.archiveFile;
        if (archiveFile == null) throw new BuildException("Attribute 'archiveFile=\"...\"' missing");

        OutputStream os = new FileOutputStream(
            AssertionUtil.notNull(this.archiveFile, "Attribute 'archiveFile=\"...\"' missing")
        );
        Closeable c = os;
        try {
            c = this.pack.setOutputStream(os);

            this.execute3();

            c.close();
        } finally {
            try { c.close(); } catch (Exception e) {}
        }
    }

    private void
    execute3() throws IOException, InterruptedException {

        ContentsProcessor<Void> contentsProcessor = this.pack.contentsProcessor();

        // We don't want directory traversal - ANT already did this for us.
        FileProcessor<Void> fileProcessor = this.pack.fileProcessor(
            false // lookIntoDirectories
        );

        // Process 'file="..."'.
        {
            File file = this.file;

            if (file != null) {
                AntTask.LOGGER.log(Level.CONFIG, "Packing ''{0}''", file.getPath());
                fileProcessor.process(file.getPath(), file);
            }
        }

        // Process resource collections.
        for (ResourceCollection resourceCollection : this.resourceCollections) {

            // Process each resource of each collection.
            for (
                @SuppressWarnings("unchecked") Iterator<Resource> it = resourceCollection.iterator();
                it.hasNext();
            ) {
                final Resource resource = it.next();

                String resourceName = resource.getName();
                AntTask.LOGGER.log(Level.CONFIG, "Packing ''{0}''", resourceName);

                if (resource.isFilesystemOnly()) {

                    // Pack file resource.
                    fileProcessor.process(resourceName, ((FileProvider) resource).getFile());
                } else {

                    // Pack non-file resource.
                    InputStream is = resource.getInputStream();
                    try {
                        contentsProcessor.process(
                            resourceName,                                         // path
                            is,                                                   // inputStream
                            resource.getSize(),                                   // size
                            -1L,                                                  // crc32
                            new ProducerWhichThrows<InputStream, IOException>() { // opener

                                @Override @Nullable public InputStream
                                produce() throws IOException { return resource.getInputStream(); }
                            }
                        );
                        is.close();
                    } finally {
                        try { is.close(); } catch (Exception e) {}
                    }
                }
            }
        }
    }
}
