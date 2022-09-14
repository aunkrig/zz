
/*
 * de.unkrig.find - An advanced version of the UNIX FIND utility
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

package de.unkrig.zz.find;

import java.io.IOException;
import java.nio.charset.Charset;
import java.security.Provider;
import java.security.Provider.Service;
import java.security.Security;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.compress.utils.Charsets;

import de.unkrig.commons.file.org.apache.commons.compress.archivers.ArchiveFormatFactory;
import de.unkrig.commons.file.org.apache.commons.compress.archivers.sevenz.SevenZArchiveFormat;
import de.unkrig.commons.file.org.apache.commons.compress.compressors.CompressionFormatFactory;
import de.unkrig.commons.file.resourceprocessing.ResourceProcessings;
import de.unkrig.commons.lang.AssertionUtil;
import de.unkrig.commons.lang.protocol.ConsumerWhichThrows;
import de.unkrig.commons.lang.protocol.ProducerUtil;
import de.unkrig.commons.text.LevelFilteredPrinter;
import de.unkrig.commons.text.Printers;
import de.unkrig.commons.text.parser.ParseException;
import de.unkrig.commons.text.pattern.Glob;
import de.unkrig.commons.text.pattern.Pattern2;
import de.unkrig.commons.util.CommandLineOptionException;
import de.unkrig.commons.util.CommandLineOptions;
import de.unkrig.commons.util.MavenUtil;
import de.unkrig.commons.util.annotation.CommandLineOption;
import de.unkrig.commons.util.annotation.RegexFlags;
import de.unkrig.commons.util.logging.SimpleLogging;
import de.unkrig.zip4jadapter.archivers.zip.ZipArchiveFormat;

/**
 * A FIND utility that can recurse into directories, archive files and compressed files.
 */
public final
class Main {

    static { AssertionUtil.enableAssertionsForThisClass(); }

    private Main() {}

    private final Find find    = new Find();

    private final LevelFilteredPrinter levelFilteredPrinter = new LevelFilteredPrinter();

    // SUPPRESS CHECKSTYLE LineLength:245
    /**
     * A command line utility to find files, directories and archive entries by various criteria, and optionally
     * execute some actions.
     * <h2>Usage</h2>
     * <dl>
     *   <dt>{@code zzfind} [ <var>option</var> ] ... <var>file-or-dir</var> ... [ <var>expression</var> ]</dt>
     *   <dd>
     *     Apply "<var>expression</var>" to "<var>file-or-dir</var> ..." and all nested directory members,
     *     archive entries and compressed files.
     *   </dd>
     * </dl>
     * <p>
     *   File name "-" stands for STDIN.
     * </p>
     *
     * <h2>Options</h2>
     *
     * <dl>
     * {@main.commandLineOptions}
     * </dl>
     *
     * <h2>Expressions</h2>
     *
     * <p>
     *   Expressions evaluate to a boolean value. An expression is either a test, or an action (a test with side
     *   effects), or a combination of multiple subexpressions through operators.
     * </p>
     *
     * <h3>Tests</h3>
     * <dl>
     *   <dt>{@code -name} <var>glob</var></dt>
     *   <dd>
     *     Name matches "<var>glob</var>" (see below).
     *   </dd>
     *
     *   <dt>{@code -path} <var>glob</var></dt>
     *   <dd>
     *     The full path (e.g. "{@code dir/file.zip!dir/file.zip!dir/file}" or "{@code dir/file}") matches
     *     "<var>glob</var>" (see below).
     *   </dd>
     *
     *   <dt>{@code -type} <var>glob</var></dt>
     *   <dd>
     *     Whether the type matches the <var>glob</var>. See the "type" property, described below.
     *     If you are unsure about types, run {@code zzfind} with <code>-echo "${type} ${path}"</code> first.
     *   </dd>
     *
     *   <dt>{@code -readable}</dt>
     *   <dd>
     *     Whether this file is readable.
     *   </dd>
     *
     *   <dt>{@code -writable}</dt>
     *   <dd>
     *     Whether this file is writable.
     *   </dd>
     *
     *   <dt>{@code -executable}</dt>
     *   <dd>
     *     Whether this file is executable.
     *   </dd>
     *
     *   <dt>
     *     {@code -size} <var>N</var>
     *     <br />
     *     {@code -size -}<var>N</var>
     *     <br />
     *     {@code -size +}<var>N</var>
     *   </dt>
     *   <dd>
     *     Whether the size is exactly/less than/more than <var>N</var> (e.g. "{@code 100}", "{@code -1K}",
     *     "{@code +10M}").
     *   </dd>
     *
     *   <dt>
     *     {@code -mtime} <var>N</var>
     *     <br />
     *     {@code -mtime +}<var>N</var>
     *     <br />
     *     {@code -mtime -}<var>N</var>
     *   </dt>
     *   <dd>
     *     Whether this file/archive entry was last modified (exactly/more than/less than) <var>N</var> days ago
     *     (N=0: 0...24h, N=1: 24...48h, ...).
     *   </dd>
     *
     *   <dt>
     *     {@code -mmin} <var>N</var>
     *     <br />
     *     {@code -mmin +}<var>N</var>
     *     <br />
     *     {@code -mmin -}<var>N</var>
     *   </dt>
     *   <dd>
     *     Whether this file/archive entry was last modified (exactly/more than/less than) <var>N</var> minutes ago
     *     (N=0: 0...59sec, N=1: 60...119sec, ...).
     *   </dd>
     * </dl>
     *
     * <h3>Actions</h3>
     * <dl>
     *   <dt>{@code -print}</dt>
     *   <dd>
     *     Print file path and return true.
     *   </dd>
     *   <dt>{@code -echo} <var>message</var></dt>
     *   <dd>
     *     Print the <var>message</var> and return true.
     *     All occurrences of "<code>${<i>property-name</i>}</code>" in the <var>message</var> are replaced with
     *     the value of the property. For the list of supported properties, see section "Properties of files and
     *     archive entries", below.
     *   </dd>
     *   <dt>{@code -printf} <var>format</var> <var>expr</var> ... {@code ;}</dt>
     *   <dd>
     *     Generate a message, print it, and return true. The <var>format</var> is the same as for Java's
     *     {@code java.util.Formatter}, and the <var>expr</var>essions support a Java-like syntax and can use all
     *     properties.
     *     For the list of supported properties, see section "Properties of files and archive entries", below.
     *     <br />
     *     Example:
     *     <blockquote>
     *       {@code -printf}                         <br />
     *       {@code '%c%c%c%c %10d %6$tF %6$tT %7$s'}<br />
     *       {@code "type == 'directory' ? 'd' : type =* 'archive-*' ? 'a' : type == 'directory-entry' ? 'D' : '-'"}<br />
     *       {@code "readable   ? 'r' : '-'"}        <br />
     *       {@code "writable   ? 'w' : '-'"}        <br />
     *       {@code "executable ? 'x' : '-'"}        <br />
     *       {@code size}                            <br />
     *       {@code lastModifiedDate}                <br />
     *       {@code path}                            <br />
     *       {@code ;}
     *     </blockquote>
     *     is equivalent with "{@code -ls}".
     *   </dd>
     *   <dt>{@code -ls}</dt>
     *   <dd>
     *     Print file type (one of "daD-"), readability, writability, executability, size, modification time and path,
     *     and return true. The size is zero for directories and directory entries, and -1 if unknown (e.g. for
     *     archive streams).
     *   </dd>
     *   <dt>{@code -exec} <var>word</var>{@code ... ;}</dt>
     *   <dd>
     *     Execute "<var>word</var>{@code ...}" as an external command; "{@code {}}" is replaced with the current
     *     file's path (which may contain "{@code !}" and would then NOT denote a physical file in the file system).
     *   </dd>
     *   <dt>{@code -pipe} <var>word</var>{@code ... ;}</dt>
     *   <dd>
     *     Copy the file contents to the standard input of an external command "<var>word</var>{@code ...}";
     *     "{@code {}}" is replaced with the current file's path (which may contain "{@code !}" and would then
     *     NOT denote a physical file in the file system).
     *   </dd>
     *   <dt>{@code -cat}</dt>
     *   <dd>
     *     Print file contents and return true.
     *   </dd>
     *   <dt>{@code -copy [ --mkdirs | -p ] } <var>tofile</var></dt>
     *   <dd>
     *     Copy file contents to the named file.
     *     All occurrences of "<code>${<i>property-name</i>}</code>" in the <var>tofile</var> are replaced with
     *     the value of the property. For the list of supported properties, see section "Properties of files and
     *     archive entries", below.
     *     <br />
     *     {@code --mkdirs} (or {@code -p}) Silently create any missing directory.
     *   </dd>
     *   <dt>
     *     {@code -disassemble} [ {@code -verbose} ] [ {@code -sourceDirectory} <var>dir</var> ] [ {@code -hideLines} ]
     *     [ {@code -hideVars} ] [ {@code -symbolicLabels} ] [ {@code -toFile} <var>file</var> ]
     *   </dt>
     *   <dd>
     *     Disassembles a Java class file (by default to STDOUT).
     *   </dd>
     *   <dt>
     *     {@code -java-class-file} <var>expr</var>
     *   </dt>
     *   <dd>
     *     Parses the Java class file, evaluates the given expression (where parameter "cf" represents the
     *     {@code de.unkrig.jdisasm.ClassFile}) and prints the result on stdout; example: "-java-class-file
     *     cf.javaVersion"
     *   </dd>
     *   <dt>{@code -digest} <var>algorithm</var></dt>
     *   <dd>
     *     Calculate a "message digest" of the contents, print it and return true.
     *     <br />
     *     Algorithms available in this environment are: ${digest.providers}
     *   </dd>
     *   <dt>{@code -checksum} CRC32|ADLER32</dt>
     *   <dd>
     *     Calculate a checksum of the contents, print it and return true.
     *     <br />
     *   </dd>
     *   <dt>{@code -true}</dt>
     *   <dd>
     *     Return {@code true}.
     *   </dd>
     *   <dt>{@code -false}</dt>
     *   <dd>
     *     Return {@code false}.
     *   </dd>
     *   <dt>{@code -prune}</dt>
     *   <dd>
     *     Return {@code true}. If the file is a directory, do not descend into it.
     *   </dd>
     *   <dt>{@code -delete}</dt>
     *   <dd>
     *     Delete file; returns {@code true} if removal succeeded. If the removal failed, an error message is issued.
     *     If you want to delete <em>directories</em>, also configure the {@code --descendants-first} option, for
     *     otherwise the directory is first deleted, and then traversed, which cannot possibly work.
     *   </dd>
     * </dl>
     *
     * <p>
     *   If no action is given, then "{@code -print}" is implicitly added.
     * </p>
     *
     * <h3>Operators</h3>
     * <p>(In descending precedence.)</p>
     * <dl>
     *   <dt>{@code (} <var>exp</var> {@code )}</dt>
     *   <dd>
     *     Whether <var>exp</var> is true.
     *   </dd>
     *
     *   <dt>
     *     {@code -not} <var>exp</var>
     *     <br />
     *     {@code !} <var>exp</var>
     *   </dt>
     *   <dd>
     *     Whether <var>exp</var> is false.
     *   </dd>
     *
     *   <dt>
     *     <var>exp1</var> {@code -a} <var>exp2</var>
     *     <br />
     *     <var>exp1</var> {@code -and} <var>exp2</var>
     *     <br />
     *     <var>exp1</var> {@code '&&'} <var>exp2</var>
     *     <br />
     *     <var>exp1</var> <var>exp2</var>
     *   </dt>
     *   <dd>
     *     Whether both <var>exp1</var> and <var>exp2</var> are true. <var>exp2</var> is not evaluated if
     *     <var>exp1</var> is false.
     *   </dd>
     *
     *   <dt>
     *     <var>exp1</var> {@code -o} <var>exp2</var>
     *     <br />
     *     <var>exp1</var> {@code -or} <var>exp2</var>
     *     <br />
     *     <var>exp1</var> {@code '||'} <var>exp2</var>
     *   </dt>
     *   <dd>
     *     Whether <var>exp1</var> or <var>exp2</var> is true. <var>exp2</var> is not evaluated if <var>exp1</var>
     *     is true.
     *   </dd>
     *
     *   <dt><var>exp1</var> {@code ,} <var>exp2</var></dt>
     *   <dd>
     *     Evaluates both expressions and returns the result of <var>exp2</var>.
     *   </dd>
     * </dl>
     *
     * <h3>Properties of files and archive entries</h3>
     * <p>
     *   Various tests and actions (e.g. "{@code -echo}") have access to a set of "variables" related to the current
     *   file or archive entry.
     * </p>
     * <dl>
     *   <dt>{@code "archiveEntry"}:</dt>
     *   <dd>
     *     For types "archive-*": The raw {@code ArchiveEntry} object, which has archive-format-specific
     *     subproperties. Use {@code archiveEntry._properties} to get a list of all accessible properties.
     *     <br />
     *     For all other types: Empty.
     *   </dd>
     *   <dt>{@code "archiveFormat"}:</dt>
     *   <dd>
     *     For types "archive-contents", "archive-file" and "archive-xxx-resource": The format.
     *     <br />
     *     For types "normal-contents", "compressed-contents" and "directory-entry": The format of the immediately
     *     enclosing archive.
     *     <br />
     *     For all other types: Empty.
     *   </dd>
     *   <dt>{@code "compressionFormat"}:</dt>
     *   <dd>
     *     For types "compressed-contents", "compressed-file" and "compressed-xxx-resource": The format.
     *     <br />
     *     For type "normal-contents", "compressed-contents" and "directory-entry": The format of the immediately
     *     enclosing compressed contents.
     *     <br />
     *     For all other types: Empty.
     *   </dd>
     *   <dt>{@code "compressionMethod"}:</dt>
     *   <dd>
     *     For archive entries: The compression method that was used.
     *     Only some of the archive formats provide this information, namely "zip" and "7z".
     *   </dd>
     *   <dt>{@code "crc"}:</dt>
     *   <dd>
     *     For types "normal-contents" and "normal-file": The CRC32 checksum of the contents.
     *     <br />
     *     For all other types: Empty.
     *   </dd>
     *   <dt>{@code "depth"}:</dt>
     *   <dd>
     *     Zero for the files/directories specified on the command line, +1 for files/directories in a subdirectory,
     *     +1 for compressed content, and +1 for the entries of archive files.
     *   </dd>
     *   <dt>{@code "file"}:</dt>
     *   <dd>
     *     For types "archive-file", "compressed-file", "directory" and "normal-file":
     *     The {@code java.util.File} object related to the current file or directory entry. It has the following
     *     properties, which can be addressed like {@code file.name}: canExecute, canRead, canWrite, exists,
     *     absoluteFile, absolutePath, canonicalFile, canonicalPath, name, parent, parentFile, path, isDirectory,
     *     isFile, isHidden, lastModified, length, toURI.
     *   </dd>
     *   <dt>{@code "lastModified"} (milliseconds since 1970),</dt>
     *   <dt>{@code "lastModifiedDate"} ({@link Date} object):</dt>
     *   <dd>
     *     For types "archive-file", "compressed-file", "directory" and "normal-file":
     *     The date and time of the last modification of the file.
     *     <br />
     *     For types "archive-xxx-resource", "compressed-xxx-resource" and "normal-xxx-resource":
     *     The the modification time of the addressed resource.
     *     <br />
     *     For types "archive-contents", "compressed-contents", "directory-entry" and "normal-contents":
     *     The the modification time stored in the archive entry.
     *   </dd>
     *   <dt>{@code "name"}:</dt>
     *   <dd>
     *     The last component of a directory or file name, or the name of an archive entry (which may contain
     *     slashes!).
     *   </dd>
     *   <dt>{@code "path"}:</dt>
     *   <dd>
     *     The path to the resources as it was found, starting with the <var>file-or-dir</var> on the command line.
     *     "!" indicates an archive, "%" a compressed file.
     *   </dd>
     *
     *   <dt>{@code "size"}:</dt>
     *   <dd>
     *     The size, in bytes, for types "archive", "archive-file", "archive-xxx-resource", "compressed-contents",
     *     "compressed-file", "compressed-xxx-resource", "file" or "normal-contents", -1 for type "directory-entry",
     *     and 0 for type "directory".
     *   </dd>
     *   <dt>{@code "type"}:</dt>
     *   <dd>
     *     The "type" of the current subject; the actual types are:
     *     <dl>
     *       <dt>{@code directory}</dt>                                <dd>A directory</dd>
     *       <dt>{@code normal-file}</dt>                              <dd>A plain file</dd>
     *       <dt>{@code normal-contents}</dt>                          <dd>Plain content in an archive or a compressed file</dd>
     *       <dt>{@code normal-xxx-resource} (e.g. xxx="http")</dt>    <dd>Plain content addressed by a URL</dd>
     *       <dt>{@code archive-file}</dt>                             <dd>An archive file</dd>
     *       <dt>{@code archive-contents}</dt>                         <dd>A nested archive</dd>
     *       <dt>{@code archive-xxx-resource} (e.g. xxx="http")</dt>   <dd>An archive addressed by a URL</dd>
     *       <dt>{@code compressed-file}</dt>                          <dd>A compressed file</dd>
     *       <dt>{@code compressed-contents}</dt>                      <dd>Nested compressed content</dd>
     *       <dt>{@code compressed-xxx-resource} (e.g. xxx="http")</dt><dd>Compressed content addressed by a URL</dd>
     *       <dt>{@code directory-entry}</dt>                          <dd>A "directory entry" in an archive.</dd>
     *     </dl>
     *   </dd>
     *   <dt>{@code "url"}:</dt>
     *   <dd>
     *     For types "normal-xxx-resource", "archive-xxx-resource" and "compressed-xxx-resource":
     *     The URL that addresses the resource.
     *   </dd>
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
        new Main().levelFilteredPrinter.run(new Runnable() {
            @Override public void run() { new Main().main2(args); }
        });
    }

    private void
    main2(String[] args) {

        try {
            args = CommandLineOptions.parse(args, this);
        } catch (CommandLineOptionException cloe) {
            Printers.error(cloe.getMessage() + ", try \"--help\".");
            System.exit(1);
        }

        // Parse the file names on the command line.
        int          i     = 0;
        List<String> files = new ArrayList<String>();
        for (; i < args.length; i++) {
            String arg = args[i];
            if ((arg.startsWith("-") && !"-".equals(arg)) || "(".equals(arg) || "!".equals(arg)) break;
            files.add(arg);
        }

        // Parse the FIND expression.
        // Notice: Even ZERO tokens are a valid FIND expression.
        try {
            Parser parser = new Parser(ProducerUtil.fromArray(args, i, args.length));
            this.find.setExpression(parser.parse());
        } catch (ParseException pe) {
            Printers.error("Parsing predicates: " + pe.getMessage());
            System.exit(1);
        } catch (Exception e) {
            Printers.error("Parsing predicates", e);
            System.exit(1);
        }

        final boolean[] hadExceptions = new boolean[1];
        ConsumerWhichThrows<IOException, IOException>
        exceptionHandler = new ConsumerWhichThrows<IOException, IOException>() {

            @Override public void
            consume(IOException ioe) {
//                Printers.error(ioe.toString());
                Printers.error(null, ioe);
                hadExceptions[0] = true;
            }
        };

        this.find.setExceptionHandler(exceptionHandler);

        // Execute the search.
        try {
            for (String file : files) {
                try {
                    this.find.findInResource(file, ResourceProcessings.toUrl(file));
                } catch (IOException ioe) {
                    exceptionHandler.consume(ioe);
                }
            }
            if (hadExceptions[0]) System.exit(2);
        } catch (Exception e) {
            Printers.error(null, e);
            System.exit(2);
        }
    }

    /**
     * Print this text and terminate.
     */
    @CommandLineOption public static void
    help() throws IOException {

        List<String> algorithms = new ArrayList<String>();
        for (Provider p : Security.getProviders()) {
            for (Service s : p.getServices()) {
                if ("MessageDigest".equals(s.getType())) {
                    algorithms.add(s.getAlgorithm());
                }
            }
        }

        System.setProperty("archive.formats",     ArchiveFormatFactory.allFormats().toString());
        System.setProperty("compression.formats", CompressionFormatFactory.allFormats().toString());
        System.setProperty("digest.providers",    algorithms.toString());

        CommandLineOptions.printResource(Main.class, "main(String[]).txt", Charset.forName("UTF-8"), System.out);

        System.exit(0);
    }

    /**
     * Print the tool version and terminate.
     */
    @CommandLineOption public static void
    version() throws IOException {
        System.out.println(MavenUtil.getMavenArtifactVersion(
            "de.unkrig.zz", // groupId
            "zz-find"       // artifactId
        ));
    }

    /**
     * Look into compressed and archive contents if the format and the path match the globs.
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
     * @param fomatAndPath <var>format-glob</var>{@code :}<var>path-glob</var>
     */
    @CommandLineOption public void
    setLookInto(@RegexFlags(Pattern2.WILDCARD | Glob.INCLUDES_EXCLUDES) Glob fomatAndPath) {
        this.find.setLookIntoFormat(fomatAndPath);
    }

    /**
     * Process each directory's contents before the directory itself, and each archive's entries before the archive
     * itself, and each compressed contents before the enclosing file or archive entry.
     */
    @CommandLineOption(name = { "depth", "descendants-first" }) public void
    setDescendantsFirst() { this.find.setDescendantsFirst(true); }

    /**
     * @see Find#setMinDepth(int)
     */
    @CommandLineOption public void
    setMinDepth(int levels) { this.find.setMinDepth(levels); }

    /**
     * Descend at most <var>levels</var> of directories below the top level files and directories. "0" means "only
     * apply the tests and actions to the top level files and directories".
     * <p>
     *   If in doubt, try:
     * </p>
     * <pre>zzfind ... -print -echo ${depth}</pre>
     */
    @CommandLineOption public void
    setMaxDepth(int levels) { this.find.setMaxDepth(levels); }

    /**
     * Password to decrypt password-protected 7ZIP input files.
     */
    @SuppressWarnings("static-method")
    @CommandLineOption public void
    set7zInputFilePassword(String value) { SevenZArchiveFormat.setPassword(value.getBytes(Charsets.UTF_16LE)); }

    /**
     * Password to decrypt password-protected zip archive entries.
     */
    @SuppressWarnings("static-method")
    @CommandLineOption public void
    setZipInputFilePassword(String value) { ZipArchiveFormat.setInputFilePasswordChars(value.toCharArray()); }

    /**
     * All of the above.
     */
    @CommandLineOption public void
    setPassword(String value) {
        this.set7zInputFilePassword(value);
        this.setZipInputFilePassword(value);
    }

    /**
     * Suppress all messages except errors.
     */
    @CommandLineOption public void
    setNowarn() {
        this.levelFilteredPrinter.setNoWarn();
        SimpleLogging.setNoWarn();
    }

    /**
     * Suppress normal output.
     */
    @CommandLineOption public void
    setQuiet() {
        this.levelFilteredPrinter.setQuiet();
        SimpleLogging.setQuiet();
    }

    /**
     * Print verbose messages.
     */
    @CommandLineOption public void
    setVerbose() {
        this.levelFilteredPrinter.setVerbose();
        SimpleLogging.setVerbose();
    }

    /**
     * Print verbose and debug messages.
     */
    @CommandLineOption public void
    setDebug() {
        this.levelFilteredPrinter.setDebug();
        SimpleLogging.setDebug();
        SimpleLogging.setDebug();
        SimpleLogging.setDebug();
    }

    /**
     * Add logging at level {@code FINE} on logger "{@code de.unkrig}" to STDERR using the "{@code FormatFormatter}"
     * and "{@code SIMPLE}" format, or the given arguments, which are all optional.
     *
     * @param spec <var>level</var>{@code :}<var>logger</var>{@code :}<var>handler</var>{@code
     *             :}<var>formatter</var>{@code :}<var>format</var>
     */
    @CommandLineOption(cardinality = CommandLineOption.Cardinality.ANY) public static void
    addLog(String spec) { SimpleLogging.configureLoggers(spec); }

    /**
     * (These are no longer supported; use "<code>--look-into</code>" instead.)
     */
    @CommandLineOption(name = { "-zip", "-zz", "-nested-zip", "-z" }) public static void
    noLongerSupported() {
        System.err.println("Command line option is no longer supported - try \"--help\".");
        System.exit(1);
    }
}
