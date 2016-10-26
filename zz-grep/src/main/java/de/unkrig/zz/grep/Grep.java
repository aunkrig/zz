
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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.nio.charset.Charset;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import de.unkrig.commons.file.ExceptionHandler;
import de.unkrig.commons.file.contentsprocessing.ContentsProcessings;
import de.unkrig.commons.file.contentsprocessing.ContentsProcessor;
import de.unkrig.commons.file.contentsprocessing.SelectiveContentsProcessor;
import de.unkrig.commons.file.fileprocessing.FileProcessings;
import de.unkrig.commons.file.fileprocessing.FileProcessor;
import de.unkrig.commons.file.org.apache.commons.compress.archivers.ArchiveFormat;
import de.unkrig.commons.file.org.apache.commons.compress.archivers.ArchiveFormatFactory;
import de.unkrig.commons.file.org.apache.commons.compress.compressors.CompressionFormat;
import de.unkrig.commons.file.org.apache.commons.compress.compressors.CompressionFormatFactory;
import de.unkrig.commons.io.ByteFilterInputStream;
import de.unkrig.commons.lang.protocol.Predicate;
import de.unkrig.commons.lang.protocol.PredicateUtil;
import de.unkrig.commons.lang.protocol.ProducerWhichThrows;
import de.unkrig.commons.nullanalysis.Nullable;
import de.unkrig.commons.text.Printers;
import de.unkrig.commons.text.pattern.Glob;
import de.unkrig.commons.util.concurrent.ConcurrentUtil;
import de.unkrig.commons.util.concurrent.SquadExecutor;

/**
 * The central API for the ZZGREP functionality.
 */
public
class Grep {

    // BEGIN CONFIGURATION VARIABLES

    /**
     * Representation of the operation that should be executed by ZZGREP.
     */
    public
    enum Operation {

        /** For each match, print the file name, a colon, a space and the matched line. */
        NORMAL,

        /** For each match, print the file name. */
        LIST,

        /** Do not print the matches. */
        QUIET,
    }

    private Predicate<? super String>     lookIntoFormat = PredicateUtil.always();
    private Charset                       charset        = Charset.defaultCharset();
    private Operation                     operation      = Operation.NORMAL;
    private boolean                       inverted;
    private boolean                       disassembleClassFiles;
    private boolean                       disassembleClassFilesButHideLines;
    private boolean                       disassembleClassFilesButHideVars;
    @Nullable private Comparator<Object>  directoryMemberNameComparator = Collator.getInstance();
    private ExceptionHandler<IOException> exceptionHandler              = ExceptionHandler.defaultHandler();

    private final
    class Search {
        final Glob    path;
        final Pattern pattern;

        /**
         * @param path    Which pathes this search applies to
         * @param pattern The regex to match the contents against
         */
        Search(Glob path, Pattern pattern) { this.path = path; this.pattern = pattern; }
    }

    private final List<Search> searches = new ArrayList<Search>();

    // END CONFIGURATION VARIABLES

    // BEGIN CONFIGURATION SETTERS

    /**
     * @param value Is evaluated against <code>"<i>format</i>:<i>path</i>"</code>
     * @see         ArchiveFormatFactory#allFormats()
     * @see         ArchiveFormat#getName()
     * @see         CompressionFormatFactory#allFormats()
     * @see         CompressionFormat#getName()
     */
    public void
    setLookIntoFormat(Predicate<? super String> value) { this.lookIntoFormat = value; }

    /** Sets a non-default charset for reading the files' contents. */
    public void
    setCharset(Charset value) { this.charset = value; }

    /**
     * The operation that should be executed by ZZGREP.
     *
     * @see Operation
     */
    public void
    setOperation(Operation value) { this.operation = value; }

    /**
     * @param value Whether matching lines should be treated as non-matching, and vice versa
     */
    public void
    setInverted(boolean value) { this.inverted = value; }

    /**
     * @param value Whether to disassemble Java&trade; class files on-the-fly before matching its contents
     */
    public void
    setDisassembleClassFiles(boolean value) { this.disassembleClassFiles = value; }

    /**
     * @param value Whether to hide source line numbers in the Java&trade; class file disassembly
     */
    public void
    setDisassembleClassFilesButHideLines(boolean value) { this.disassembleClassFilesButHideLines = value; }

    /**
     * @param value Whether to local variable names in the Java&trade; class file disassembly
     */
    public void
    setDisassembleClassFilesButHideVars(boolean value) { this.disassembleClassFilesButHideVars = value; }

    /**
     * @param path  Which pathes the search applies to
     * @param regex The regular expression to match each line against
     */
    public void
    addSearch(Glob path, String regex, boolean caseSensitive) {

        this.searches.add(
            new Search(path, Pattern.compile(regex, caseSensitive ? 0 : Pattern.CASE_INSENSITIVE))
        );
    }

    /**
     * Sets the exception handler.
     */
    public void
    setExceptionHandler(ExceptionHandler<IOException> exceptionHandler) { this.exceptionHandler = exceptionHandler; }

    /**
     * @param directoryMemberNameComparator The comparator used to sort a directory's members; a {@code null} value
     *                                      means to NOT sort the members, i.e. leave them in their 'natural' order as
     *                                      {@link File#list()} returns them
     */
    public void
    setDirectoryMemberNameComparator(@Nullable Comparator<Object> directoryMemberNameComparator) {
        this.directoryMemberNameComparator = directoryMemberNameComparator;
    }

    // END CONFIGURATION SETTERS

    // BEGIN SEARCH RESULT VARIABLES

    /**
     * Whether the last invocation of {@link #grep()} yielded one or more matches.
     */
    boolean linesSelected;

    // END SEARCH RESULT VARIABLES

    // BEGIN SEARCH RESULT GETTERS

    /**
     * Whether the invocation of the {@link #contentsProcessor()} yielded one or more matches.
     */
    public boolean
    getLinesSelected() { return this.linesSelected; }

    // END SEARCH RESULT GETTERS

    /**
     * @return A {@link ContentsProcessor} which executes the search and prints results to STDOUT
     */
    public ContentsProcessor<Void>
    contentsProcessor() {

        return new ContentsProcessor<Void>() {

            @Override @Nullable public Void
            process(
                String                                                            path,
                InputStream                                                       is,
                long                                                              size,
                long                                                              crc32,
                ProducerWhichThrows<? extends InputStream, ? extends IOException> opener
            ) throws IOException {

//                if (!Grep.this.includeExclude.matches(name)) return null;

                final List<Pattern> patterns = new ArrayList<Pattern>();
                for (Search search : Grep.this.searches) {
                    if (search.path.matches(path)) patterns.add(search.pattern);
                }

                if (patterns.isEmpty()) return null;

                if (Grep.this.disassembleClassFiles && path.endsWith(".class")) {

                    // Wrap the input stream in a Java disassembler.
                    DisassemblerByteFilter disassemblerByteFilter = new DisassemblerByteFilter();
                    disassemblerByteFilter.setHideLines(Grep.this.disassembleClassFilesButHideLines);
                    disassemblerByteFilter.setHideVars(Grep.this.disassembleClassFilesButHideVars);
                    is = new ByteFilterInputStream(is, disassemblerByteFilter);
                }

                PushbackReader pbr = new PushbackReader(
                    new BufferedReader(new InputStreamReader(is, Grep.this.charset))
                );
                for (;;) {
                    String line = Grep.readLine(pbr);
                    if (line == null) break;

                    boolean found = false;
                    for (Pattern pattern : patterns) {
                        if (pattern.matcher(line).find()) { found = true; break; }
                    }

                    if (found ^ Grep.this.inverted) {
                        Grep.this.linesSelected = true;
                        switch (Grep.this.operation) {

                        case NORMAL:
                            Printers.info(path + ": " + line);
                            break;

                        case LIST:
                            Printers.info(path);
                            return null;

                        case QUIET:
                            return null;
                        }
                    }
                }
                return null;
            }
        };
    }

    /**
     * @return A {@link FileProcessor} which executes the search and prints results to STDOUT
     */
    public FileProcessor<Void>
    fileProcessor(boolean lookIntoDirectories) {

        // Process only the files to which at least one search applies.
        Predicate<? super String> pathPredicate = PredicateUtil.never();
        for (Search search : this.searches) {
            pathPredicate = PredicateUtil.or(pathPredicate, search.path);
        }

        FileProcessor<Void> fp = FileProcessings.recursiveCompressedAndArchiveFileProcessor(
            this.lookIntoFormat,                            // lookIntoFormat
            ContentsProcessings.<Void>nopArchiveCombiner(), // archiveEntryCombiner
            new SelectiveContentsProcessor<Void>(           // contentsProcessor
                pathPredicate,
                this.contentsProcessor(),
                ContentsProcessings.<Void>nopContentsProcessor()
            ),
            this.exceptionHandler                           // exceptionHandler
        );

        // Honor the 'lookIntoDirectories' flag.
        if (lookIntoDirectories) {
            fp = FileProcessings.<Void>directoryTreeProcessor(
                pathPredicate,                                                       // pathPredicate
                fp,                                                                  // regularFileProcessor
                this.directoryMemberNameComparator,                                  // directoryMemberNameComparator
                FileProcessings.<Void>nopDirectoryCombiner(),                        // directoryCombiner
                new SquadExecutor<Void>(ConcurrentUtil.SEQUENTIAL_EXECUTOR_SERVICE), // squadExecutor
                this.exceptionHandler                                                // exceptionHandler
            );
        }

        return fp;
    }

    /**
     * Similar to {@link BufferedReader#readLine()}, but avoids memory problems with excessively long lines.
     *
     * @ return The next line, or {@code null} on end-of-input
     */
    @Nullable static String
    readLine(PushbackReader pbr) throws IOException {

        int c  = pbr.read();
        if (c == -1) return null;

        StringBuilder sb = new StringBuilder(1000);
        for (;;) {
            if (c == -1) break;   // Unterminated last line.
            if (c == '\n') break; // Line with UNIX terminator (LF).
            if (c == '\r') {      // Line with MAC terminator (CR).
                c = pbr.read();
                if (c != '\n') pbr.unread(c); // Line with DOS terminator (CR LF).
                break;
            }

            // Some (binary) files contain lines as long as 100,000,000 characters - not a good idea to read these
            // into a String. Just ignore all but the first 64k characters of each line.
            if (sb.length() < 65536) {
                sb.append((char) c);
            }
            c = pbr.read();
        }
        return sb.toString();
    }
}
