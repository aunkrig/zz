
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

// SUPPRESS CHECKSTYLE Javadoc:9999

package test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.junit.Test;

import de.unkrig.commons.file.FileUtil;
import de.unkrig.commons.junit4.AssertRegex;
import de.unkrig.commons.lang.AssertionUtil;
import de.unkrig.commons.lang.protocol.ConsumerUtil;
import de.unkrig.commons.lang.protocol.Mapping;
import de.unkrig.commons.lang.protocol.ProducerUtil;
import de.unkrig.commons.lang.protocol.RunnableWhichThrows;
import de.unkrig.commons.text.AbstractPrinter;
import de.unkrig.commons.text.Printers;
import de.unkrig.commons.text.parser.ParseException;
import de.unkrig.commons.text.pattern.Glob;
import de.unkrig.commons.text.pattern.Pattern2;
import de.unkrig.commons.text.pattern.PatternUtil;
import de.unkrig.zz.find.Find;
import de.unkrig.zz.find.Parser;
import junit.framework.TestCase;

/**
 * Tests for the {@link Find} class.
 */
public
class FindTest extends TestCase {

    private static final File FILES = new File("files");

    @Override protected void
    setUp() throws IOException {
        if (FindTest.FILES.exists()) FileUtil.deleteRecursively(FindTest.FILES);
        new Files(new Object[] {
            "dir1", new Object[] {
                "dir2", new Object[] {
                    "file1",   "",
                    "file3.Z", "123",
                    "file.zip", new Object[] {
                        "/dir1/dir2/file1",  "XYZ",
                        "dir3/dir4/file2",   "",
                        "dir3/dir4/dir5",    null,
                        "dir3/dir4/file3.Z", "456",
                        "file.zip", new Object[] {
                            "/dir1/dir2/file1",  "ABC",
                            "dir3/dir4/file2",   "",
                            "dir3/dir4/dir5",    null,
                            "dir3/dir4/file3.Z", "789",
                        },
                    },
                },
            },
        }).save(FindTest.FILES);
    }

    @Override protected void
    tearDown() throws IOException {
        FileUtil.deleteRecursively(FindTest.FILES);
    }

    @Test public void
    test1() throws Exception {

        Find find = new Find();
        find.setLookIntoFormat(Glob.compile("zip:", Pattern2.WILDCARD | Glob.INCLUDES_EXCLUDES));

        FindTest.assertFindOutputMatches(
            find,                              // find
            new String[] { "-name", "file1" }, // expression
            "@FILES/dir1/dir2/file1"           // expectedRegexes
        );
    }

    @Test public void
    test2() throws Exception {

        Find find = new Find();
        find.setLookIntoFormat(Glob.compile("zip:**", Pattern2.WILDCARD | Glob.INCLUDES_EXCLUDES));

        FindTest.assertFindOutputMatches(
            find,                                         // find
            new String[] { "-name", "**file1" },          // expression
            "@FILES/dir1/dir2/file.zip!/dir1/dir2/file1", // expectedRegexes
            "@FILES/dir1/dir2/file1"
        );
    }

    @Test public void
    test3() throws Exception {

        Find find = new Find();
        find.setLookIntoFormat(Glob.compile("zip:***", Pattern2.WILDCARD | Glob.INCLUDES_EXCLUDES));

        FindTest.assertFindOutputMatches(
            find,                                           // find
            new String[] { "-name", "**file1" },            // expression
            "@FILES/dir1/dir2/file\\.zip!/dir1/dir2/file1", // expectedRegexes...
            "@FILES/dir1/dir2/file\\.zip!file\\.zip!/dir1/dir2/file1",
            "@FILES/dir1/dir2/file1"
        );
    }

    @Test public void
    test4() throws Exception {

        FindTest.assertFindOutputMatches(
            new Find(),                            // find
            new String[] { "-type", "directory" }, // expression
            "@FILES",                              // expectedRegexes...
            "@FILES/dir1",
            "@FILES/dir1/dir2"
        );
    }

    @Test public void
    test5() throws Exception {

        FindTest.assertFindOutputMatches(
            new Find(),                               // find
            new String[] { "-type", "archive-file" }, // expression
            "@FILES/dir1/dir2/file\\.zip"             // expectedRegexes...
        );
    }

    @Test public void
    test6() throws Exception {

        FindTest.assertFindOutputMatches(
            new Find(),
            new String[] { "-type", "compressed-file" },
            "@FILES/dir1/dir2/file3\\.Z"
        );
    }

    @Test public void
    test7() throws Exception {

        FindTest.assertFindOutputMatches(
            new Find(),
            new String[] { "-type", "normal-file" },
            "@FILES/dir1/dir2/file1"
        );
    }

    @Test public void
    test8() throws Exception {

        FindTest.assertFindOutputMatches(
            new Find(),
            new String[] { "-type", "archive-contents" },
            "@FILES/dir1/dir2/file\\.zip!file\\.zip"
        );
    }

    @Test public void
    test9() throws Exception {

        FindTest.assertFindOutputMatches(
            new Find(),                                     // find
            new String[] { "-type", "normal-contents" },    // expression
            "@FILES/dir1/dir2/file\\.zip!/dir1/dir2/file1", // expectedRegexes...
            "@FILES/dir1/dir2/file\\.zip!dir3/dir4/file2",
            "@FILES/dir1/dir2/file\\.zip!dir3/dir4/file3\\.Z%",
            "@FILES/dir1/dir2/file\\.zip!file\\.zip!/dir1/dir2/file1",
            "@FILES/dir1/dir2/file\\.zip!file\\.zip!dir3/dir4/file2",
            "@FILES/dir1/dir2/file\\.zip!file\\.zip!dir3/dir4/file3\\.Z%",
            "@FILES/dir1/dir2/file3\\.Z%"
        );
    }

    @Test public void
    test10() throws Exception {

        FindTest.assertFindOutputMatches(
            new Find(),                                  // find
            new String[] { "-type", "directory-entry" }, // expression
            "@FILES/dir1/dir2/file.zip!dir3/dir4/dir5",  // expectedRegexes...
            "@FILES/dir1/dir2/file.zip!file\\.zip!dir3/dir4/dir5"
        );
    }

    /**
     * Tests the magical {@code _map} property, which is the map of <i>all</i> properties.
     */
    @Test public void
    testExpressionVariables() throws Exception {

        Find find = new Find();
        find.setExpression(new Find.Expression() {

            @Override public boolean
            evaluate(Mapping<String, Object> properties) {

                @SuppressWarnings("unchecked") Map<String, Object> map = (Map<String, Object>) properties.get("_map");

                // Sort the map by key value to make equality assertions deterministic.
                map = new TreeMap<>(map);

                Printers.info(map.toString());

                return true;
            }
        });

        FindTest.assertFindOutputMatches(
            find, // SUPPRESS CHECKSTYLE LineLength:18
            "\\{\\$PRUNE=\\[Z@\\w+, depth=0, executable=true, file=@ABS_FILES, inputStream=null, lastModified=@ANY_INT, lastModifiedDate=@ANY_DATE, name=files, path=@FILES, readable=true, type=directory, writable=true}",
            "\\{\\$PRUNE=\\[Z@\\w+, depth=1, executable=true, file=@ABS_FILES/dir1, inputStream=null, lastModified=@ANY_INT, lastModifiedDate=@ANY_DATE, name=dir1, path=@FILES/dir1, readable=true, type=directory, writable=true}",
            "\\{\\$PRUNE=\\[Z@\\w+, depth=2, executable=true, file=@ABS_FILES/dir1/dir2, inputStream=null, lastModified=@ANY_INT, lastModifiedDate=@ANY_DATE, name=dir2, path=@FILES/dir1/dir2, readable=true, type=directory, writable=true}",
            "\\{\\$PRUNE=\\[Z@\\w+, archiveFormat=zip, crc=@ANY_INT, depth=3, executable=true, file=@ABS_FILES/dir1/dir2/file\\.zip, inputStream=null, lastModified=@ANY_INT, lastModifiedDate=@ANY_DATE, name=file\\.zip, path=@FILES/dir1/dir2/file\\.zip, readable=true, size=@ANY_INT, type=archive-file, url=file:/\\S*/files/dir1/dir2/file.zip, writable=true}",
            "\\{archiveEntry=/dir1/dir2/file1, archiveFormat=zip, compressionMethod=DEFLATED, crc=2099902701, depth=4, executable=false, inputStream=java\\.io\\.BufferedInputStream@\\w+, lastModified=@ANY_INT, lastModifiedDate=@ANY_DATE, name=/dir1/dir2/file1, path=@FILES/dir1/dir2/file\\.zip!/dir1/dir2/file1, readable=true, size=3, type=normal-contents, writable=false}",
            "\\{archiveEntry=dir3/dir4/file2, archiveFormat=zip, compressionMethod=DEFLATED, crc=0, depth=4, executable=false, inputStream=java\\.io\\.BufferedInputStream@\\w+, lastModified=@ANY_INT, lastModifiedDate=@ANY_DATE, name=dir3/dir4/file2, path=@FILES/dir1/dir2/file\\.zip!dir3/dir4/file2, readable=true, size=0, type=normal-contents, writable=false}",
            "\\{archiveEntry=dir3/dir4/dir5/, archiveFormat=zip, compressionMethod=(STORED|DEFLATED), crc=(0|-1), depth=4, executable=false, inputStream=null, lastModified=@ANY_INT, lastModifiedDate=@ANY_DATE, name=dir3/dir4/dir5, path=@FILES/dir1/dir2/file\\.zip!dir3/dir4/dir5, readable=true, size=0, type=directory-entry, writable=false}",
            "\\{archiveEntry=dir3/dir4/file3\\.Z, archiveFormat=zip, compressionFormat=gz, compressionMethod=(STORED|DEFLATED), crc=@ANY_INT, depth=4, executable=false, inputStream=null, lastModified=@ANY_INT, lastModifiedDate=@ANY_DATE, name=dir3/dir4/file3\\.Z, path=@FILES/dir1/dir2/file\\.zip!dir3/dir4/file3\\.Z, readable=true, size=23, type=compressed-contents, writable=false}",
            "\\{archiveEntry=dir3/dir4/file3.Z, archiveFormat=zip, compressionFormat=gz, compressionMethod=DEFLATED, crc=0, depth=5, executable=false, inputStream=java\\.io\\.BufferedInputStream@\\w+, lastModified=@ANY_INT, lastModifiedDate=@ANY_DATE, name=dir3/dir4/file3\\.Z%, path=@FILES/dir1/dir2/file\\.zip!dir3/dir4/file3\\.Z%, readable=true, size=3, type=normal-contents, writable=false}",
            "\\{\\$PRUNE=\\[Z@\\w+, archiveEntry=file\\.zip, archiveFormat=zip, compressionMethod=DEFLATED, crc=@ANY_INT, depth=4, executable=false, inputStream=null, lastModified=@ANY_INT, lastModifiedDate=@ANY_DATE, name=file\\.zip, path=@FILES/dir1/dir2/file\\.zip!file\\.zip, readable=true, size=@ANY_INT, type=archive-contents, writable=false}",
            "\\{archiveEntry=/dir1/dir2/file1, archiveFormat=zip, compressionMethod=DEFLATED, crc=0, depth=5, executable=false, inputStream=java\\.io\\.BufferedInputStream@\\w+, lastModified=@ANY_INT, lastModifiedDate=@ANY_DATE, name=/dir1/dir2/file1, path=@FILES/dir1/dir2/file\\.zip!file\\.zip!/dir1/dir2/file1, readable=true, size=3, type=normal-contents, writable=false}",
            "\\{archiveEntry=dir3/dir4/file2, archiveFormat=zip, compressionMethod=DEFLATED, crc=0, depth=5, executable=false, inputStream=java\\.io\\.BufferedInputStream@\\w+, lastModified=@ANY_INT, lastModifiedDate=@ANY_DATE, name=dir3/dir4/file2, path=@FILES/dir1/dir2/file\\.zip!file\\.zip!dir3/dir4/file2, readable=true, size=0, type=normal-contents, writable=false}",
            "\\{archiveEntry=dir3/dir4/dir5/, archiveFormat=zip, compressionMethod=(STORED|DEFLATED), crc=(0|-1), depth=5, executable=false, inputStream=null, lastModified=@ANY_INT, lastModifiedDate=@ANY_DATE, name=dir3/dir4/dir5, path=@FILES/dir1/dir2/file\\.zip!file\\.zip!dir3/dir4/dir5, readable=true, size=0, type=directory-entry, writable=false}",
            "\\{archiveEntry=dir3/dir4/file3\\.Z, archiveFormat=zip, compressionFormat=gz, compressionMethod=DEFLATED, crc=-1, depth=5, executable=false, inputStream=null, lastModified=@ANY_INT, lastModifiedDate=@ANY_DATE, name=dir3/dir4/file3\\.Z, path=@FILES/dir1/dir2/file\\.zip!file\\.zip!dir3/dir4/file3\\.Z, readable=true, size=-1, type=compressed-contents, writable=false}",
            "\\{archiveEntry=dir3/dir4/file3\\.Z, archiveFormat=zip, compressionFormat=gz, compressionMethod=DEFLATED, crc=0, depth=6, executable=false, inputStream=java\\.io\\.BufferedInputStream@\\w+, lastModified=@ANY_INT, lastModifiedDate=@ANY_DATE, name=dir3/dir4/file3\\.Z%, path=@FILES/dir1/dir2/file\\.zip!file\\.zip!dir3/dir4/file3\\.Z%, readable=true, size=3, type=normal-contents, writable=false}",
            "\\{crc=0, depth=3, executable=true, file=@ABS_FILES/dir1/dir2/file1, inputStream=de\\.unkrig\\.commons\\.io\\.InputStreams\\$9@\\w+, lastModified=@ANY_INT, lastModifiedDate=@ANY_DATE, name=file1, path=@FILES/dir1/dir2/file1, readable=true, size=0, type=normal-file, url=file:\\S+/zz-find/files/dir1/dir2/file1, writable=true}",
            "\\{compressionFormat=gz, crc=-1900763251, depth=3, executable=true, file=@ABS_FILES/dir1/dir2/file3\\.Z, inputStream=null, lastModified=@ANY_INT, lastModifiedDate=@ANY_DATE, name=file3\\.Z, path=@FILES/dir1/dir2/file3\\.Z, readable=true, size=23, type=compressed-file, url=file:\\S+/zz-find/files/dir1/dir2/file3.Z, writable=true}",
            "\\{compressionFormat=gz, crc=0, depth=4, executable=true, file=@ABS_FILES/dir1/dir2/file3.Z, inputStream=java\\.io\\.BufferedInputStream@\\w+, lastModified=@ANY_INT, lastModifiedDate=@ANY_DATE, name=file3\\.Z%, path=@FILES/dir1/dir2/file3\\.Z%, readable=true, size=3, type=normal-contents, url=file:\\S+/zz-find/files/dir1/dir2/file3.Z, writable=true}"
        );
    }

    @Test public void
    testDescendantsFirst() throws Exception {

        Find find = new Find();
        find.setDescendantsFirst(true);

        FindTest.assertFindOutputMatches(
            find,                                                // find
            new String[] { "-echo", "path=${path}" },            // expression
            "path=@FILES/dir1/dir2/file\\.zip!/dir1/dir2/file1", // expectedRegexes...
            "path=@FILES/dir1/dir2/file\\.zip!dir3/dir4/file2",
            "path=@FILES/dir1/dir2/file\\.zip!dir3/dir4/dir5",
            "path=@FILES/dir1/dir2/file\\.zip!dir3/dir4/file3\\.Z%",
            "path=@FILES/dir1/dir2/file\\.zip!dir3/dir4/file3\\.Z",
            "path=@FILES/dir1/dir2/file\\.zip!file\\.zip!/dir1/dir2/file1",
            "path=@FILES/dir1/dir2/file\\.zip!file\\.zip!dir3/dir4/file2",
            "path=@FILES/dir1/dir2/file\\.zip!file\\.zip!dir3/dir4/dir5",
            "path=@FILES/dir1/dir2/file\\.zip!file\\.zip!dir3/dir4/file3\\.Z%",
            "path=@FILES/dir1/dir2/file\\.zip!file\\.zip!dir3/dir4/file3\\.Z",
            "path=@FILES/dir1/dir2/file\\.zip!file\\.zip",
            "path=@FILES/dir1/dir2/file\\.zip",
            "path=@FILES/dir1/dir2/file1",
            "path=@FILES/dir1/dir2/file3\\.Z%",
            "path=@FILES/dir1/dir2/file3\\.Z",
            "path=@FILES/dir1/dir2",
            "path=@FILES/dir1",
            "path=@FILES"
        );
    }

    @Test public void
    testMinDepth2() throws Exception {

        Find find = new Find();
        find.setMinDepth(2);

        FindTest.assertFindOutputMatches(
            find,                                     // find
            new String[] { "-echo", "path=${path}" }, // expression
//            "path=@FILES",                          // expectedRegexes...
//            "path=@FILES/dir1",
            "path=@FILES/dir1/dir2",
            "path=@FILES/dir1/dir2/file\\.zip",
            "path=@FILES/dir1/dir2/file\\.zip!/dir1/dir2/file1",
            "path=@FILES/dir1/dir2/file\\.zip!dir3/dir4/file2",
            "path=@FILES/dir1/dir2/file\\.zip!dir3/dir4/dir5",
            "path=@FILES/dir1/dir2/file\\.zip!dir3/dir4/file3\\.Z",
            "path=@FILES/dir1/dir2/file\\.zip!dir3/dir4/file3\\.Z%",
            "path=@FILES/dir1/dir2/file\\.zip!file\\.zip",
            "path=@FILES/dir1/dir2/file\\.zip!file\\.zip!/dir1/dir2/file1",
            "path=@FILES/dir1/dir2/file\\.zip!file\\.zip!dir3/dir4/file2",
            "path=@FILES/dir1/dir2/file\\.zip!file\\.zip!dir3/dir4/dir5",
            "path=@FILES/dir1/dir2/file\\.zip!file\\.zip!dir3/dir4/file3\\.Z",
            "path=@FILES/dir1/dir2/file\\.zip!file\\.zip!dir3/dir4/file3\\.Z%",
            "path=@FILES/dir1/dir2/file1",
            "path=@FILES/dir1/dir2/file3\\.Z",
            "path=@FILES/dir1/dir2/file3\\.Z%"
        );
    }

    @Test public void
    testMinDepth5() throws Exception {

        Find find = new Find();
        find.setMinDepth(5);

        FindTest.assertFindOutputMatches(
            find,                                     // find
            new String[] { "-echo", "path=${path}" }, // expression
//            "path=@FILES",                          // expectedRegexes...
//            "path=@FILES/dir1",
//            "path=@FILES/dir1/dir2",
//            "path=@FILES/dir1/dir2/file\\.zip",
//            "path=@FILES/dir1/dir2/file\\.zip!/dir1/dir2/file1",
//            "path=@FILES/dir1/dir2/file\\.zip!dir3/dir4/file2",
//            "path=@FILES/dir1/dir2/file\\.zip!dir3/dir4/dir5",
//            "path=@FILES/dir1/dir2/file\\.zip!dir3/dir4/file3\\.Z",
            "path=@FILES/dir1/dir2/file\\.zip!dir3/dir4/file3\\.Z%",
//            "path=@FILES/dir1/dir2/file\\.zip!file\\.zip",
            "path=@FILES/dir1/dir2/file\\.zip!file\\.zip!/dir1/dir2/file1",
            "path=@FILES/dir1/dir2/file\\.zip!file\\.zip!dir3/dir4/file2",
            "path=@FILES/dir1/dir2/file\\.zip!file\\.zip!dir3/dir4/dir5",
            "path=@FILES/dir1/dir2/file\\.zip!file\\.zip!dir3/dir4/file3\\.Z",
            "path=@FILES/dir1/dir2/file\\.zip!file\\.zip!dir3/dir4/file3\\.Z%"
//            "path=@FILES/dir1/dir2/file1",
//            "path=@FILES/dir1/dir2/file3\\.Z",
//            "path=@FILES/dir1/dir2/file3\\.Z%"
        );
    }

    @Test public void
    testMaxDepth2() throws Exception {

        Find find = new Find();
        find.setMaxDepth(2);

        FindTest.assertFindOutputMatches(
            find,                                     // find
            new String[] { "-echo", "path=${path}" }, // expression
            "path=@FILES",                            // expectedRegexes...
            "path=@FILES/dir1",
            "path=@FILES/dir1/dir2"
//            "path=@FILES/dir1/dir2/file\\.zip",
//            "path=@FILES/dir1/dir2/file\\.zip!/dir1/dir2/file1",
//            "path=@FILES/dir1/dir2/file\\.zip!dir3/dir4/file2",
//            "path=@FILES/dir1/dir2/file\\.zip!dir3/dir4/dir5",
//            "path=@FILES/dir1/dir2/file\\.zip!dir3/dir4/file3\\.Z",
//            "path=@FILES/dir1/dir2/file\\.zip!dir3/dir4/file3\\.Z%",
//            "path=@FILES/dir1/dir2/file\\.zip!file\\.zip",
//            "path=@FILES/dir1/dir2/file\\.zip!file\\.zip!/dir1/dir2/file1",
//            "path=@FILES/dir1/dir2/file\\.zip!file\\.zip!dir3/dir4/file2",
//            "path=@FILES/dir1/dir2/file\\.zip!file\\.zip!dir3/dir4/dir5",
//            "path=@FILES/dir1/dir2/file\\.zip!file\\.zip!dir3/dir4/file3\\.Z",
//            "path=@FILES/dir1/dir2/file\\.zip!file\\.zip!dir3/dir4/file3\\.Z%",
//            "path=@FILES/dir1/dir2/file1",
//            "path=@FILES/dir1/dir2/file3\\.Z",
//            "path=@FILES/dir1/dir2/file3\\.Z%"
        );
    }

    @Test public void
    testMaxDepth4() throws Exception {

        Find find = new Find();
        find.setMaxDepth(4);

        FindTest.assertFindOutputMatches(
            find,                                     // find
            new String[] { "-echo", "path=${path}" }, // expression
            "path=@FILES",                            // expectedRegexes...
            "path=@FILES/dir1",
            "path=@FILES/dir1/dir2",
            "path=@FILES/dir1/dir2/file\\.zip",
            "path=@FILES/dir1/dir2/file\\.zip!/dir1/dir2/file1",
            "path=@FILES/dir1/dir2/file\\.zip!dir3/dir4/file2",
            "path=@FILES/dir1/dir2/file\\.zip!dir3/dir4/dir5",
            "path=@FILES/dir1/dir2/file\\.zip!dir3/dir4/file3\\.Z",
//            "path=@FILES/dir1/dir2/file\\.zip!dir3/dir4/file3\\.Z%",
            "path=@FILES/dir1/dir2/file\\.zip!file\\.zip",
//            "path=@FILES/dir1/dir2/file\\.zip!file\\.zip!/dir1/dir2/file1",
//            "path=@FILES/dir1/dir2/file\\.zip!file\\.zip!dir3/dir4/file2",
//            "path=@FILES/dir1/dir2/file\\.zip!file\\.zip!dir3/dir4/dir5",
//            "path=@FILES/dir1/dir2/file\\.zip!file\\.zip!dir3/dir4/file3\\.Z",
//            "path=@FILES/dir1/dir2/file\\.zip!file\\.zip!dir3/dir4/file3\\.Z%",
            "path=@FILES/dir1/dir2/file1",
            "path=@FILES/dir1/dir2/file3\\.Z",
            "path=@FILES/dir1/dir2/file3\\.Z%"
        );
    }

    @Test public void
    testDigest() throws Exception {

        // SUPPRESS CHECKSTYLE LineLength|Wrap:6
        FindTest.assertFindOutputMatches(
            new Find(),                                                                                        // find
            new String[] { "-name", "***file3.Z%", "-print", "-digest", "MD5" },                               // expression
            "@FILES/dir1/dir2/file\\.zip!dir3/dir4/file3\\.Z%",            "250cf8b51c773f3f8dc8b4be867a9a02", // The MD5 of "456"
            "@FILES/dir1/dir2/file\\.zip!file\\.zip!dir3/dir4/file3\\.Z%", "68053af2923e00204c3ca7c6a3150cf7", // The MD5 of "789"
            "@FILES/dir1/dir2/file3\\.Z%",                                 "202cb962ac59075b964b07152d234b70"  // The MD5 of "123"
        );
    }

    @Test public void
    testChecksum() throws Exception {

        // SUPPRESS CHECKSTYLE Wrap:6
        FindTest.assertFindOutputMatches(
            new Find(),                                                                // find
            new String[] { "-name", "***file3.Z%", "-print", "-checksum", "CRC32" },   // expression
            "@FILES/dir1/dir2/file\\.zip!dir3/dir4/file3\\.Z%",            "b1a8c371", // CRC32 of "456"
            "@FILES/dir1/dir2/file\\.zip!file\\.zip!dir3/dir4/file3\\.Z%", "96ff1ef4", // CRC32 of "789"
            "@FILES/dir1/dir2/file3\\.Z%",                                 "884863d2"  // CRC32 of "123"
        );
    }

    @Test public void
    testEcho() throws Exception {

        FindTest.assertFindOutputMatches(
            new Find(),                                                        // find
            new String[] {                                                     // expression
                "-name", FindTest.FILES.getName(),
                "-echo", "${import java.util.regex.*; Pattern.compile(\"A\")}"
            },
            "A"                                                                // expectedRegexes...
        );
    }

    @Test public void
    testLs() throws Exception {

        FindTest.assertFindOutputMatches(
            new Find(),                                   // find
            new String[] { "-ls" },                       // expression
            "drwx          0 ....-..-.. ..:..:.. @FILES", // expectedRegexes
            "drwx          0 ....-..-.. ..:..:.. @FILES/dir1",
            "drwx          0 ....-..-.. ..:..:.. @FILES/dir1/dir2",
            "arwx        ... ....-..-.. ..:..:.. @FILES/dir1/dir2/file\\.zip",
            "-r--          3 ....-..-.. ..:..:.. @FILES/dir1/dir2/file\\.zip!/dir1/dir2/file1",
            "-r--          0 ....-..-.. ..:..:.. @FILES/dir1/dir2/file\\.zip!dir3/dir4/file2",
            "Dr--          0 ....-..-.. ..:..:.. @FILES/dir1/dir2/file\\.zip!dir3/dir4/dir5",
            "-r--         23 ....-..-.. ..:..:.. @FILES/dir1/dir2/file\\.zip!dir3/dir4/file3\\.Z",
            "-r--          3 ....-..-.. ..:..:.. @FILES/dir1/dir2/file\\.zip!dir3/dir4/file3\\.Z%",
            "ar--        ... ....-..-.. ..:..:.. @FILES/dir1/dir2/file\\.zip!file\\.zip",
            "-r--          3 ....-..-.. ..:..:.. @FILES/dir1/dir2/file\\.zip!file\\.zip!/dir1/dir2/file1",
            "-r--          0 ....-..-.. ..:..:.. @FILES/dir1/dir2/file\\.zip!file\\.zip!dir3/dir4/file2",
            "Dr--          0 ....-..-.. ..:..:.. @FILES/dir1/dir2/file\\.zip!file\\.zip!dir3/dir4/dir5",
            "-r--         -1 ....-..-.. ..:..:.. @FILES/dir1/dir2/file\\.zip!file\\.zip!dir3/dir4/file3\\.Z",
            "-r--          3 ....-..-.. ..:..:.. @FILES/dir1/dir2/file\\.zip!file\\.zip!dir3/dir4/file3\\.Z%",
            "-rwx          0 ....-..-.. ..:..:.. @FILES/dir1/dir2/file1",
            "-rwx         23 ....-..-.. ..:..:.. @FILES/dir1/dir2/file3\\.Z",
            "-rwx          3 ....-..-.. ..:..:.. @FILES/dir1/dir2/file3\\.Z%"
        );
    }

    /**
     * Executes a {@link Find} search with the given <var>expression</var> and asserts that the produced output matches
     * the <var>expectedRegexes</var>.
     *
     * @param expression The FIND expression, e.g. <code>{ "-echo", "${path}" }</code>
     * @param expectedRegexes (Will be pre-cooked by {@link #cookRegex(String)})
     */
    private static void
    assertFindOutputMatches(Find find, String[] expression, String... expectedRegexes) throws Exception {

        AssertRegex.assertMatches(
            Arrays.asList(FindTest.cookRegexes(expectedRegexes)),
            FindTest.find(find, expression)
        );
    }

    private static void
    assertFindOutputMatches(Find find, String... expectedRegexes) throws Exception {

        AssertRegex.assertMatches(Arrays.asList(FindTest.cookRegexes(expectedRegexes)), FindTest.find(find));
    }

    private static String[]
    cookRegexes(String... regexes) {
        String[] result = new String[regexes.length];
        for (int i = 0; i < regexes.length; i++) result[i] = FindTest.cookRegex(regexes[i]);
        return result;
    }

    /**
     * Cooks the <var>regex</var> in a {@link FindTest}-specific way. Sections that are replaced begin with a
     * {@code "@"}, e.g. {@code "@ANY_INT"} is replaced with {@code "-?\\d+"}.
     */
    private static String
    cookRegex(String regex) {

        regex = PatternUtil.replaceSome(
            Pattern.compile("@ABS_FILES(?!/)").matcher(regex),
            mr -> FindTest.ap(FindTest.FILES)
        );
        regex = PatternUtil.replaceSome(
            Pattern.compile("@ABS_FILES/([\\w/]*)").matcher(regex),
            mr -> FindTest.ap(FindTest.FILES, AssertionUtil.notNull(mr).group(1))
        );

        regex = PatternUtil.replaceSome(
            Pattern.compile("@FILES(?!/)").matcher(regex),
            mr -> FindTest.p(FindTest.FILES)
        );
        regex = PatternUtil.replaceSome(
            Pattern.compile("@FILES/([\\w/]*)").matcher(regex),
            mr -> FindTest.p(FindTest.FILES, AssertionUtil.notNull(mr).group(1))
        );

        regex = regex.replace("@ANY_DATE", "\\w{3} \\w{3} \\d\\d \\d\\d:\\d\\d:\\d\\d \\w+ \\d{4}");

        regex = regex.replace("@ANY_INT",  "-?\\d+");

        return regex;
    }

    /**
     * @return The path of the file represented by the <var>dir</var> and <var>name</var>
     */
    private static String
    p(File dir, String name) { return FindTest.p(new File(dir, name)); }

    /**
     * @return The path of the <var>file</var>
     */
    private static String
    p(File file) { return Pattern.quote(file.getPath()); }

    /**
     * @return The absolute path of the file represented by the <var>dir</var> and <var>name</var>
     */
    private static String
    ap(File dir, String name) { return FindTest.ap(new File(dir, name)); }

    /**
     * @return The absolute path of the <var>file</var>
     */
    private static String
    ap(File file) { return Pattern.quote(file.getAbsolutePath()); }

    /**
     * Executes a {@link Find} search on the {@link #FILES} directory (see {@link #setUp()}) with the given
     * <var>expression</var>.
     *
     * @param expression The FIND expression, e.g. <code>{ "-echo", "${path}" }</code>
     * @return           The lines of INFO output (see {@link Printers#info(String)})
     */
    private static List<String>
    find(final Find find, String[] expression) throws ParseException, IOException {

        Parser parser = new Parser(ProducerUtil.fromElements(expression));
        find.setExpression(parser.parse());

        return FindTest.find(find);
    }

    /**
     * Executes a FIND on the {@link #FILES} directory (see {@link #setUp()}).
     *
     * @param find The pre-configured {@link Find} object to use
     * @return     The lines of INFO output (see {@link Printers#info(String)})
     */
    private static List<String>
    find(final Find find) throws IOException {

        List<String> lines = new ArrayList<String>();
        AbstractPrinter.getContextPrinter().redirectInfo(
            ConsumerUtil.addToCollection(lines)
        ).run((RunnableWhichThrows<IOException>) () -> find.findInFile(FindTest.FILES));

        return lines;
    }
}
