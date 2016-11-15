
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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.unkrig.commons.file.ExceptionHandler;
import de.unkrig.commons.file.contentstransformation.ContentsTransformations;
import de.unkrig.commons.file.contentstransformation.ContentsTransformer;
import de.unkrig.commons.file.contentstransformation.SelectiveContentsTransformer;
import de.unkrig.commons.file.filetransformation.FileTransformations;
import de.unkrig.commons.file.filetransformation.FileTransformations.ArchiveCombiner;
import de.unkrig.commons.file.filetransformation.FileTransformations.DirectoryCombiner;
import de.unkrig.commons.file.filetransformation.FileTransformations.NameAndContents;
import de.unkrig.commons.file.filetransformation.FileTransformer;
import de.unkrig.commons.file.org.apache.commons.compress.archivers.ArchiveFormatFactory;
import de.unkrig.commons.file.org.apache.commons.compress.compressors.CompressionFormatFactory;
import de.unkrig.commons.lang.protocol.ConsumerWhichThrows;
import de.unkrig.commons.lang.protocol.Predicate;
import de.unkrig.commons.lang.protocol.PredicateUtil;
import de.unkrig.commons.nullanalysis.Nullable;
import de.unkrig.commons.text.pattern.Glob;
import de.unkrig.commons.text.pattern.IncludeExclude;

/**
 * Implementation of a PATCH utility with the following features:
 * <ul>
 *   <li>Transforms regular files, directory trees, and optionally entries in ZIP files (also in nested ones)
 *   <li>Reads patch files in NORMAL, CONTEXT and UNIFIED diff format
 *   <li>Can replace the contents of files from "update files"
 *   <li>Can do search-and-replace within files (SED like)
 *   <li>Can transform out-of-place or in-place
 *   <li>Optionally keeps copies of the original files
 *   <li>Can remove files
 *   <li>Can rename files
 *   <li>Can add files
 * </ul>
 */
public
class Patch {

    private static final Logger LOGGER = Logger.getLogger(Patch.class.getName());

    private static
    class ContentsTransformation {

        public Predicate<? super String> pathPredicate;
        public ContentsTransformer       contentsTransformer;

        ContentsTransformation(Predicate<? super String> pathPredicate, ContentsTransformer contentsTransformer) {
            this.pathPredicate       = pathPredicate;
            this.contentsTransformer = contentsTransformer;
        }
    }

    private
    interface Combiner extends DirectoryCombiner, ArchiveCombiner {}

    private static final Combiner
    DEFAULT_COMBINER = new Combiner() {

        @Override public void
        combineDirectory(
            String                                                              directoryPath,
            ConsumerWhichThrows<? super NameAndContents, ? extends IOException> memberAdder
        ) {}

        @Override public void
        combineArchive(
            String                                                              archivePath,
            ConsumerWhichThrows<? super NameAndContents, ? extends IOException> entryAdder
        ) {}
    };

    private boolean                             saveSpace = true;
    private boolean                             keepOriginals;
    private final List<ContentsTransformation>  contentsTransformations       = new ArrayList<ContentsTransformation>();
    private Predicate<? super String>           fileAndArchiveEntryRemoval    = PredicateUtil.never();
    private final IncludeExclude                fileAndArchiveEntryRenaming   = new IncludeExclude();
    private Combiner                            combiner                      = Patch.DEFAULT_COMBINER;
    private Predicate<? super String>           lookIntoFormat                = PredicateUtil.always();
    @Nullable private Comparator<Object>        directoryMemberNameComparator = Collator.getInstance();

    private ExceptionHandler<IOException> exceptionHandler = ExceptionHandler.<IOException>defaultHandler();

    /**
     * Whether to transfor directory trees file-by-file ({@code true}, or to create a temporary copy of the entire
     * directory tree ({@code false}). With the latter option chances are bigger that the original files can be
     * reverted in case of an exception.
     * <p>
     * Only relevant for in-place transformations.
     */
    public void
    setSaveSpace(boolean value) {
        Patch.LOGGER.log(Level.FINER, "setSaveSpace(''{0}'')", value);

        this.saveSpace = value;
    }

    /**
     * Whether to keep backup copies of files/entries that are modified or removed. Default is {@code false}.
     */
    public void
    setKeepOriginals(boolean value) {
        Patch.LOGGER.log(Level.FINER, "setKeepOriginals(''{0}'')", value);

        this.keepOriginals = value;
    }

    /**
     * For any archive contents, instead of the 'raw' contents, the contents of the <i>archive entries</i> are
     * processed iff the string
     * <p>
     *   <code>"</code><i>archive-format</i><code>:</code><i>archive-path</i><code>"</code>
     * <p>
     * matches the given predicate.
     * <p>
     * For any compressed contents, instead of the 'raw' contents, the <i>expanded</i> contents is processed iff the
     * string
     * <p>
     *   <code>"</code><i>compression-format</i><code>:</code><i>compressed-path</i><code>"</code>
     * <p>
     * matches the given predicate.
     * <p>
     * The default is {@link PredicateUtil#always()}.
     *
     * @see ArchiveFormatFactory#allFormats()
     * @see CompressionFormatFactory#allFormats()
     */
    public void
    setLookIntoFormat(Predicate<? super String> value) {
        Patch.LOGGER.log(Level.FINER, "setLookInto(''{0}'')", value);

        this.lookIntoFormat = value;
    }

    /**
     * @param directoryMemberNameComparator The comparator used to sort a directory's members; a {@code null} value
     *                                      means to NOT sort the members, i.e. leave them in their 'natural' order as
     *                                      {@link File#list()} returns them
     */
    public void
    setDirectoryMemberNameComparator(@Nullable Comparator<Object> directoryMemberNameComparator) {
        this.directoryMemberNameComparator = directoryMemberNameComparator;
    }

    /**
     * @return The currently set exception handler
     */
    public ExceptionHandler<IOException>
    getExceptionHandler() { return this.exceptionHandler; }

    /**
     * Sets the exception handler. The default is {@link ExceptionHandler#defaultHandler()}.
     */
    public void
    setExceptionHandler(ExceptionHandler<IOException> exceptionHandler) { this.exceptionHandler = exceptionHandler; }

    /**
     * For each file and ZIP entry, the first matching {@link de.unkrig.zz.patch.Patch.ContentsTransformation} takes
     * effect.
     *
     * @return The <var>contentsTransformer</var>
     */
    public ContentsTransformer
    addContentsTransformation(Predicate<? super String> pathPredicate, ContentsTransformer contentsTransformer) {

        Patch.LOGGER.log(
            Level.FINER,
            "addContentsTransformation(''{0}'', ''{1}'')",
            new Object[] { pathPredicate, contentsTransformer }
        );

        this.contentsTransformations.add(0, new ContentsTransformation(pathPredicate, contentsTransformer));

        return contentsTransformer;
    }

    /**
     * Configures that files and ZIP entries who's pathes match the {@code removal} will be removed.
     */
    public void
    addRemoval(Predicate<? super String> removal) {
        Patch.LOGGER.log(Level.FINER, "addRemoval(''{0}'')", removal);

        this.fileAndArchiveEntryRemoval = PredicateUtil.or(this.fileAndArchiveEntryRemoval, removal);
    }

    /**
     * Configures that files and ZIP entries which match the {@code removal} will be renamed. Multiple renamings are
     * applied in order, i.e. "a=b", "b=c" is effectively "a=c".
     */
    public void
    addRenaming(Glob value) {
        Patch.LOGGER.log(Level.FINER, "addRenaming(''{0}'')", value);
        this.fileAndArchiveEntryRenaming.addInclude(value, false);
    }

    /**
     * @param condition Determines whether the contents will be added
     * @param name      File name or archive entry name to add
     * @param contents  Contents to add
     */
    public void
    addAddition(final Predicate<? super String> condition, final String name, final File contents) {
        Patch.LOGGER.log(
            Level.FINER,
            "addAddition(''{0}'', ''{1}'', ''{2}'')",
            new Object[] { condition, name, contents }
        );

        final NameAndContents nac = new NameAndContents() {

            @Override public String
            getName() { return name; }

            @Override public InputStream
            open() throws FileNotFoundException { return new FileInputStream(contents); }

            @Override public String
            toString() { return name; }
        };

        final Combiner old = this.combiner;
        this.combiner = new Combiner() {

            @Override public void
            combineDirectory(
                String                                                              directoryPath,
                ConsumerWhichThrows<? super NameAndContents, ? extends IOException> memberAdder
            ) throws IOException {
                old.combineDirectory(directoryPath, memberAdder);

                if (condition.evaluate(directoryPath)) {
                    Patch.LOGGER.log(Level.CONFIG, "{0}: Adding ''{1}''", new Object[] { directoryPath, nac });
                    memberAdder.consume(nac);
                }
            }

            @Override public void
            combineArchive(
                String                                                              archivePath,
                ConsumerWhichThrows<? super NameAndContents, ? extends IOException> entryAdder
            ) throws IOException {
                old.combineArchive(archivePath, entryAdder);

                if (condition.evaluate(archivePath)) {
                    Patch.LOGGER.log(Level.CONFIG, "{0}: Adding ''{1}''", new Object[] { archivePath, nac });
                    entryAdder.consume(nac);
                }
            }
        };
    }

    /**
     * @param lookIntoDirectories         Iff {@code false}, then the returned {@link FileTransformations} can solely
     *                                    transform regular files
     * @param renameOrRemoveTopLevelFiles Iff {@code false}, then only files in subdirectories and archive entries are
     *                                    renamed and/or removed
     * @return                            A {@link FileTransformer} which executes directory tree / archive file /
     *                                    compressed file traversal and executes the previously configured operations
     */
    public FileTransformer
    fileTransformer(boolean lookIntoDirectories, boolean renameOrRemoveTopLevelFiles) {

        // Create a contents transformer which implements compressed contents and archive traversal.
        ContentsTransformer contentsTransformer = this.rawContentsTransformer();

        // Wrap the contents transformer in a compressed and archive file transformer.
        FileTransformer
        fileTransformer = FileTransformations.recursiveCompressedAndArchiveFileTransformer(
            this.lookIntoFormat,
            this.fileAndArchiveEntryRemoval,  // archiveEntryRemoval
            this.fileAndArchiveEntryRenaming, // archiveEntryRenaming
            this.combiner,                    // archiveCombiner
            contentsTransformer,              // delegate
            this.keepOriginals,               // keepOriginals
            this.exceptionHandler             // exceptionHandler
        );

        if (lookIntoDirectories) {

            // Re-wrap the archive file transformer in a directory tree transformer.
            fileTransformer = FileTransformations.directoryTreeTransformer(
                this.directoryMemberNameComparator, // directoryMemberNameComparator
                this.fileAndArchiveEntryRemoval,    // directoryMemberRemoval
                this.fileAndArchiveEntryRenaming,   // directoryMemberRenaming
                this.combiner,                      // directoryCombiner
                fileTransformer,                    // regularFileTransformer
                this.saveSpace,                     // saveSpace
                this.keepOriginals,                 // keepOriginals
                this.exceptionHandler               // exceptionHandler
            );
        }

        if (renameOrRemoveTopLevelFiles) {

            // Re-wrap the file transformer for file renaming and removal.
            fileTransformer = FileTransformations.renameRemoveFileTransformer(
                this.fileAndArchiveEntryRemoval,  // removal
                this.fileAndArchiveEntryRenaming, // renaming
                fileTransformer,                  // delegate
                this.keepOriginals                // keepOriginals
            );
        }

        return fileTransformer;
    }

    /**
     * @return A {@link ContentsTransformer} which implements compressed contents and archive traversal and executes
     *         the previously configured operations
     */
    public ContentsTransformer
    contentsTransformer() {

        ContentsTransformer contentsTransformer = this.rawContentsTransformer();

        // Wrap the contents transformer in a compressed/archive file transformer.
        contentsTransformer = ContentsTransformations.recursiveCompressedAndArchiveContentsTransformer(
            this.lookIntoFormat,              // lookIntoFormat
            this.fileAndArchiveEntryRemoval,  // archiveEntryRemoval
            this.fileAndArchiveEntryRenaming, // archiveEntryRenaming
            this.combiner,                    // archiveCombiner
            contentsTransformer,
            this.exceptionHandler
        );

        return contentsTransformer;
    }

    /*
     * @return A contents transformer that implements all the requested contents transformations (updates,
     *         substitutions, patches).
     */
    private ContentsTransformer
    rawContentsTransformer() {

        ContentsTransformer contentsTransformer = ContentsTransformations.COPY;

        for (ContentsTransformation ct : this.contentsTransformations) {
            contentsTransformer = new SelectiveContentsTransformer(
                ct.pathPredicate,
                ct.contentsTransformer,
                contentsTransformer
            );
        }
        return contentsTransformer;
    }
}
