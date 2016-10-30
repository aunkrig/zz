
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
import java.io.OutputStream;
import java.nio.charset.Charset;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.compressors.CompressorException;

import de.unkrig.commons.file.ExceptionHandler;
import de.unkrig.commons.file.fileprocessing.FileProcessor;
import de.unkrig.commons.file.org.apache.commons.compress.archivers.ArchiveFormatFactory;
import de.unkrig.commons.file.org.apache.commons.compress.archivers.zip.ZipArchiveFormat;
import de.unkrig.commons.file.org.apache.commons.compress.compressors.CompressionFormatFactory;
import de.unkrig.commons.text.LevelFilteredPrinter;
import de.unkrig.commons.text.Printers;
import de.unkrig.commons.text.pattern.Glob;
import de.unkrig.commons.text.pattern.Pattern2;
import de.unkrig.commons.util.CommandLineOptions;
import de.unkrig.commons.util.annotation.CommandLineOption;
import de.unkrig.commons.util.annotation.RegexFlags;
import de.unkrig.commons.util.logging.SimpleLogging;

/**
 * The ZZGREP command line utility.
 */
public final
class Main {

    private Main() {}

    private final Pack pack = new Pack();
    {

        // Use "ZIP" as the default archive format.
        this.pack.setArchiveFormat(ZipArchiveFormat.get());

        this.pack.setExceptionHandler(new ExceptionHandler<IOException>() {
            @Override public void handle(String path, IOException ioe)     { Printers.error(path, ioe); }
            @Override public void handle(String path, RuntimeException re) { Printers.error(path, re); }
        });
    }
    private final LevelFilteredPrinter levelFilteredPrinter = new LevelFilteredPrinter(Printers.get());

    /**
     * <h2>Usage:</h2>
     * <dl>
     *   <dt>{@code zzpack} [ <var>option</var> ] ... [ - ] <var>archive-file</var> <var>file-or-dir</var> ...</dt>
     *   <dd>
     *     Pack all of <var>file-or-dir</var> ... into <var>archive-file</var>.
     *   </dd>
     * </dl>
     *
     * <h2>Options</h2>
     *
     * <dl>
     *   {@main.commandLineOptions}
     * </dl>
     *
     * <h2>Example <var>glob</var>s</h2>
     *
     * <dl>
     *   <dt>{@code dir/file}</dt>
     *   <dd>
     *     File "{@code file}" in directory "{@code dir}"
     *   </dd>
     *   <dt>{@code file.gz%}</dt>
     *   <dd>
     *     Compressed file "{@code file.gz}"
     *   </dd>
     *   <dt>{@code file.zip!dir/file}</dt>
     *   <dd>
     *     Entry "{@code dir/file}" in archive file "{@code dir/file.zip}"
     *   </dd>
     *   <dt>{@code file.tar.gz%!dir/file}</dt>
     *   <dd>
     *     Entry "{@code dir/file}" in the compressed archive file "{@code file.tar.gz}"
     *   </dd>
     *   <dt><code>&#42;/x</code></dt>
     *   <dd>
     *     File "{@code x}" in an immediate subdirectory
     *   </dd>
     *   <dt><code>*&#42;/x</code></dt>
     *   <dd>
     *     File "{@code x}" in any subdirectory
     *   </dd>
     *   <dt><code>**&#42;/x</code></dt>
     *   <dd>
     *     File "{@code x}" in any subdirectory, or any entry "<code>*&#42;/x</code>" in any archive file in any
     *     subdirectory
     *   </dd>
     *   <dt>{@code a,dir/file.7z!dir/b}</dt>
     *   <dd>
     *     File "{@code a}" and entry "{@code dir/b}" in archive file "{@code dir/file.7z}"
     *   </dd>
     *   <dt>{@code ~*.c}</dt>
     *   <dd>
     *     Files that don't end with "{@code .c}"
     *   </dd>
     *   <dt>{@code ~*.c~*.h}</dt>
     *   <dd>
     *     Files that don't end with "{@code .c}" or "{@code .h}"
     *   </dd>
     *   <dt>{@code ~*.c~*.h,foo.c}</dt>
     *   <dd>
     *     "{@code foo.c}" plus all files that don't end with "{@code .c}" or "{@code .h}"
     *   </dd>
     * </dl>
     */
    public static void
    main(final String[] args) {

        final Main main = new Main();
        Printers.withPrinter(main.levelFilteredPrinter, new Runnable() {
            @Override public void run() { main.main2(args); }
        });
    }

    private void
    main2(String[] args) {
        try {
            this.main3(args);
        } catch (Exception e) {
            Printers.error(null, e);
        }
    }

    private void
    main3(String[] args) throws Exception {

        args = CommandLineOptions.parse(args, this);

        // Get archive file.
        if (args.length == 0) {
            System.err.println("Archive file missing, try \"--help\".");
            System.exit(1);
        }
        File archiveFile = new File(args[0]);

        // Get input files.
        if (args.length == 1) {
            System.err.println("Input files missing, try \"--help\".");
            System.exit(1);
        }

        FileProcessor<Void> fp = this.pack.fileProcessor(true);

        OutputStream os = new FileOutputStream(archiveFile);
        Closeable    c  = os;
        try {
            c = this.pack.setOutputStream(os);
            for (int i = 1; i < args.length; i++) {
                File file = new File(args[i]);
                fp.process(file.getPath(), file);
            }
            c.close();
        } finally {
            try { c.close(); } catch (Exception e) {}
        }
    }

    /**
     * Print this text and terminate.
     */
    @CommandLineOption public static void
    help() throws IOException {

        System.setProperty("archive.formats",     ArchiveFormatFactory.allFormats().toString());
        System.setProperty("compression.formats", CompressionFormatFactory.allFormats().toString());

        CommandLineOptions.printResource(Main.class, "main(String[]).txt", Charset.forName("UTF-8"), System.out);

        System.exit(0);
    }

    /**
     * The archive format to use (one of ${archive.formats}; defaults to "zip").
     */
    @CommandLineOption public void
    setArchiveFormat(String format) throws ArchiveException {
        this.pack.setArchiveFormat(ArchiveFormatFactory.forFormatName(format));
    }

    /**
     * The format to use for the compression of the archive file (one of ${compression.formats}) (optional)
     */
    @CommandLineOption public void
    setCompressionFormat(String format) throws CompressorException {
        this.pack.setCompressionFormat(CompressionFormatFactory.forFormatName(format));
    }

    /**
     * By default directory members are sorted lexicographically in order to achieve deterministic results.
     */
    @CommandLineOption public void
    dontSortDirectoryMembers() { this.pack.setDirectoryMemberNameComparator(null); }

    /**
     * Look into compressed and archive contents if their <var>format</var>:<var>path</var> matches the glob.
     * The default is to look into any recognised archive or compressed contents.
     * <br />
     * Supported archive formats in this runtime configuration are:
     * <br />
     * {@code ${archive.formats}}
     * <br />
     * Supported compression formats in this runtime configuration are:
     * <br />
     * {@code ${compression.formats}}
     *
     * @param discriminator <var>format-glob</var>:<var>path-glob</var>
     */
    @CommandLineOption public void
    lookInto(@RegexFlags(Pattern2.WILDCARD | Glob.INCLUDES_EXCLUDES) Glob discriminator) {
        this.pack.setLookIntoFormat(discriminator);
    }

    /**
     * Suppress all messages except errors.
     */
    @CommandLineOption public void
    nowarn() { this.levelFilteredPrinter.setNoWarn(); }

    /**
     * Suppress all normal output.
     */
    @CommandLineOption(name = { "q", "quiet" }) public void
    quiet() { this.levelFilteredPrinter.setQuiet(); }

    /**
     * Print verbose messages.
     */
    @CommandLineOption public void
    verbose() { this.levelFilteredPrinter.setVerbose(); }

    /**
     * Print verbose and debug messages.
     */
    @CommandLineOption public void
    debug() { this.levelFilteredPrinter.setDebug(); }

    /**
     * Add logging at level FINE on logger 'de.unkrig' to STDERR using the FormatFormatter and SIMPLE format, or the
     * given arguments which are all optional.
     *
     * @param spec <var>level</var>:<var>logger</var>:<var>handler</var>:<var>formatter</var>:<var>format</var>
     */
    @CommandLineOption(cardinality = CommandLineOption.Cardinality.ANY) public static void
    addLog(String spec) { SimpleLogging.configureLoggers(spec); }

    /**
     * @deprecated Use {@code --look-into} instead.
     */
    @Deprecated @CommandLineOption(name = { "zip", "zz", "nested-zip", "gzip" }) public static void
    noLongerSupported() {
        System.err.println("Command line option is no longer supported - try \"--help\".");
        System.exit(1);
    }
}
