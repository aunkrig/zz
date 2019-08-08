
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

import org.junit.Test;

import de.unkrig.commons.file.FileUtil;
import de.unkrig.commons.junit4.AssertRegex;
import de.unkrig.commons.lang.protocol.ConsumerUtil;
import de.unkrig.commons.lang.protocol.ProducerUtil;
import de.unkrig.commons.lang.protocol.RunnableWhichThrows;
import de.unkrig.commons.text.AbstractPrinter;
import de.unkrig.commons.text.parser.ParseException;
import de.unkrig.commons.text.pattern.Glob;
import de.unkrig.commons.text.pattern.Pattern2;
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
                    "file1",    "",
                    "file3.Z",  "123",
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

        FindTest.assertFindOutputEquals(
            find,
            new String[] { "-name", "file1" },
            new File(FindTest.FILES, "dir1/dir2/file1").getPath()
        );
    }

    @Test public void
    test2() throws Exception {

        Find find = new Find();
        find.setLookIntoFormat(Glob.compile("zip:**", Pattern2.WILDCARD | Glob.INCLUDES_EXCLUDES));

        FindTest.assertFindOutputEquals(
            find,
            new String[] { "-name", "**file1" },
            new File(FindTest.FILES, "dir1/dir2/file.zip").getPath() + "!/dir1/dir2/file1",
            new File(FindTest.FILES, "dir1/dir2/file1").getPath()
        );
    }

    @Test public void
    test3() throws Exception {

        Find find = new Find();
        find.setLookIntoFormat(Glob.compile("zip:***", Pattern2.WILDCARD | Glob.INCLUDES_EXCLUDES));

        FindTest.assertFindOutputEquals(
            find,
            new String[] { "-name", "**file1" },
            new File(FindTest.FILES, "dir1/dir2/file.zip").getPath() + "!/dir1/dir2/file1",
            new File(FindTest.FILES, "dir1/dir2/file.zip").getPath() + "!file.zip!/dir1/dir2/file1",
            new File(FindTest.FILES, "dir1/dir2/file1").getPath()
        );
    }

    @Test public void
    test4() throws Exception {

        FindTest.assertFindOutputEquals(
            new Find(),
            new String[] { "-type", "directory" },
            FindTest.FILES.getPath(),
            new File(FindTest.FILES, "dir1").getPath(),
            new File(FindTest.FILES, "dir1/dir2").getPath()
        );
    }

    @Test public void
    test5() throws Exception {

        FindTest.assertFindOutputEquals(
            new Find(),
            new String[] { "-type", "archive-file" },
            new File(FindTest.FILES, "dir1/dir2/file.zip").getPath()
        );
    }

    @Test public void
    test6() throws Exception {

        FindTest.assertFindOutputEquals(
            new Find(),
            new String[] { "-type", "compressed-file" },
            new File(FindTest.FILES, "dir1/dir2/file3.Z").getPath()
        );
    }

    @Test public void
    test7() throws Exception {

        FindTest.assertFindOutputEquals(
            new Find(),
            new String[] { "-type", "normal-file" },
            new File(FindTest.FILES, "dir1/dir2/file1").getPath()
        );
    }

    @Test public void
    test8() throws Exception {

        FindTest.assertFindOutputEquals(
            new Find(),
            new String[] { "-type", "archive-contents" },
            new File(FindTest.FILES, "dir1/dir2/file.zip").getPath() + "!file.zip"
        );
    }

    @Test public void
    test9() throws Exception {

        FindTest.assertFindOutputEquals(
            new Find(),
            new String[] { "-type", "normal-contents" },
            new File(FindTest.FILES, "dir1/dir2/file.zip").getPath() + "!/dir1/dir2/file1",
            new File(FindTest.FILES, "dir1/dir2/file.zip").getPath() + "!dir3/dir4/file2",
            new File(FindTest.FILES, "dir1/dir2/file.zip").getPath() + "!dir3/dir4/file3.Z%",
            new File(FindTest.FILES, "dir1/dir2/file.zip").getPath() + "!file.zip!/dir1/dir2/file1",
            new File(FindTest.FILES, "dir1/dir2/file.zip").getPath() + "!file.zip!dir3/dir4/file2",
            new File(FindTest.FILES, "dir1/dir2/file.zip").getPath() + "!file.zip!dir3/dir4/file3.Z%",
            new File(FindTest.FILES, "dir1/dir2/file3.Z").getPath() + '%'
        );
    }

    @Test public void
    test10() throws Exception {

        FindTest.assertFindOutputEquals(
            new Find(),
            new String[] { "-type", "directory-entry" },
            new File(FindTest.FILES, "dir1/dir2/file.zip").getPath() + "!dir3/dir4/dir5",
            new File(FindTest.FILES, "dir1/dir2/file.zip").getPath() + "!file.zip!dir3/dir4/dir5"
        );
    }

    @Test public void
    testPrint() throws Exception {

        // SUPPRESS CHECKSTYLE LineLength:27
        FindTest.assertFindOutputMatches(
            new Find(),
            new String[] { "-echo", (
                "name=${name} path=${path} type=${type} absolutePath=${absolutePath} canonicalPath=${canonicalPath} "
                + "lastModifiedDate=${lastModifiedDate} size=${size} isDirectory=${isDirectory} isFile=${isFile} "
                + "isHidden=${isHidden} isReadable=${isReadable} isWritable=${isWritable} isExecutable=${isExecutable} "
                + "archiveFormat=${archiveFormat} compressionFormat=${compressionFormat} depth=${depth}"
            ) },
            "name=files path=files type=directory absolutePath=\\S+files canonicalPath=\\S+files lastModifiedDate=... ... .. ..:..:.. \\w+ .... size=0 isDirectory=true isFile=false isHidden=false isReadable=true isWritable=true isExecutable=true archiveFormat= compressionFormat= depth=0",
            "name=dir1 path=files\\\\dir1 type=directory absolutePath=\\S+files\\\\dir1 canonicalPath=\\S+files\\\\dir1 lastModifiedDate=... ... .. ..:..:.. \\w+ .... size=0 isDirectory=true isFile=false isHidden=false isReadable=true isWritable=true isExecutable=true archiveFormat= compressionFormat= depth=1",
            "name=dir2 path=files\\\\dir1\\\\dir2 type=directory absolutePath=\\S+files\\\\dir1\\\\dir2 canonicalPath=\\S+files\\\\dir1\\\\dir2 lastModifiedDate=... ... .. ..:..:.. \\w+ .... size=0 isDirectory=true isFile=false isHidden=false isReadable=true isWritable=true isExecutable=true archiveFormat= compressionFormat= depth=2",
            "name=file.zip path=files\\\\dir1\\\\dir2\\\\file.zip type=archive-file absolutePath=\\S+files\\\\dir1\\\\dir2\\\\file.zip canonicalPath=\\S+files\\\\dir1\\\\dir2\\\\file.zip lastModifiedDate=... ... .. ..:..:.. \\w+ .... size=\\d+ isDirectory=false isFile=true isHidden=false isReadable=true isWritable=true isExecutable=true archiveFormat=zip compressionFormat= depth=3",
            "name=/dir1/dir2/file1 path=files\\\\dir1\\\\dir2\\\\file.zip!/dir1/dir2/file1 type=normal-contents absolutePath=\\S+files\\\\dir1\\\\dir2\\\\file.zip canonicalPath=\\S+files\\\\dir1\\\\dir2\\\\file.zip lastModifiedDate=... ... .. ..:..:.. \\w+ .... size=3 isDirectory=false isFile=true isHidden=false isReadable=true isWritable=true isExecutable=true archiveFormat=zip compressionFormat= depth=4",
            "name=dir3/dir4/file2 path=files\\\\dir1\\\\dir2\\\\file.zip!dir3/dir4/file2 type=normal-contents absolutePath=\\S+files\\\\dir1\\\\dir2\\\\file.zip canonicalPath=\\S+files\\\\dir1\\\\dir2\\\\file.zip lastModifiedDate=... ... .. ..:..:.. \\w+ .... size=0 isDirectory=false isFile=true isHidden=false isReadable=true isWritable=true isExecutable=true archiveFormat=zip compressionFormat= depth=4",
            "name=dir3/dir4/dir5 path=files\\\\dir1\\\\dir2\\\\file.zip!dir3/dir4/dir5 type=directory-entry absolutePath=\\S+files\\\\dir1\\\\dir2\\\\file.zip canonicalPath=\\S+files\\\\dir1\\\\dir2\\\\file.zip lastModifiedDate=... ... .. ..:..:.. \\w+ .... size=0 isDirectory=true isFile=true isHidden=false isReadable=true isWritable=true isExecutable=true archiveFormat=zip compressionFormat= depth=4",
            "name=dir3/dir4/file3.Z path=files\\\\dir1\\\\dir2\\\\file.zip!dir3/dir4/file3.Z type=compressed-contents absolutePath=\\S+files\\\\dir1\\\\dir2\\\\file.zip canonicalPath=\\S+files\\\\dir1\\\\dir2\\\\file.zip lastModifiedDate=... ... .. ..:..:.. \\w+ .... size=\\d+ isDirectory=false isFile=true isHidden=false isReadable=true isWritable=true isExecutable=true archiveFormat=zip compressionFormat=gz depth=4",
            "name=dir3/dir4/file3.Z% path=files\\\\dir1\\\\dir2\\\\file.zip!dir3/dir4/file3.Z% type=normal-contents absolutePath=\\S+files\\\\dir1\\\\dir2\\\\file.zip canonicalPath=\\S+files\\\\dir1\\\\dir2\\\\file.zip lastModifiedDate=... ... .. ..:..:.. \\w+ .... size=3 isDirectory=false isFile=true isHidden=false isReadable=true isWritable=true isExecutable=true archiveFormat=zip compressionFormat=gz depth=5",
            "name=file.zip path=files\\\\dir1\\\\dir2\\\\file.zip!file.zip type=archive-contents absolutePath=\\S+files\\\\dir1\\\\dir2\\\\file.zip canonicalPath=\\S+files\\\\dir1\\\\dir2\\\\file.zip lastModifiedDate=... ... .. ..:..:.. \\w+ .... size=\\d+ isDirectory=false isFile=true isHidden=false isReadable=true isWritable=true isExecutable=true archiveFormat=zip compressionFormat= depth=4",
            "name=/dir1/dir2/file1 path=files\\\\dir1\\\\dir2\\\\file.zip!file.zip!/dir1/dir2/file1 type=normal-contents absolutePath=\\S+files\\\\dir1\\\\dir2\\\\file.zip canonicalPath=\\S+files\\\\dir1\\\\dir2\\\\file.zip lastModifiedDate=... ... .. ..:..:.. \\w+ .... size=3 isDirectory=false isFile=true isHidden=false isReadable=true isWritable=true isExecutable=true archiveFormat=zip compressionFormat= depth=5",
            "name=dir3/dir4/file2 path=files\\\\dir1\\\\dir2\\\\file.zip!file.zip!dir3/dir4/file2 type=normal-contents absolutePath=\\S+files\\\\dir1\\\\dir2\\\\file.zip canonicalPath=\\S+files\\\\dir1\\\\dir2\\\\file.zip lastModifiedDate=... ... .. ..:..:.. \\w+ .... size=0 isDirectory=false isFile=true isHidden=false isReadable=true isWritable=true isExecutable=true archiveFormat=zip compressionFormat= depth=5",
            "name=dir3/dir4/dir5 path=files\\\\dir1\\\\dir2\\\\file.zip!file.zip!dir3/dir4/dir5 type=directory-entry absolutePath=\\S+files\\\\dir1\\\\dir2\\\\file.zip canonicalPath=\\S+files\\\\dir1\\\\dir2\\\\file.zip lastModifiedDate=... ... .. ..:..:.. \\w+ .... size=-1 isDirectory=true isFile=true isHidden=false isReadable=true isWritable=true isExecutable=true archiveFormat=zip compressionFormat= depth=5",
            "name=dir3/dir4/file3.Z path=files\\\\dir1\\\\dir2\\\\file.zip!file.zip!dir3/dir4/file3.Z type=compressed-contents absolutePath=\\S+files\\\\dir1\\\\dir2\\\\file.zip canonicalPath=\\S+files\\\\dir1\\\\dir2\\\\file.zip lastModifiedDate=... ... .. ..:..:.. \\w+ .... size=-1 isDirectory=false isFile=true isHidden=false isReadable=true isWritable=true isExecutable=true archiveFormat=zip compressionFormat=gz depth=5",
            "name=dir3/dir4/file3.Z% path=files\\\\dir1\\\\dir2\\\\file.zip!file.zip!dir3/dir4/file3.Z% type=normal-contents absolutePath=\\S+files\\\\dir1\\\\dir2\\\\file.zip canonicalPath=\\S+files\\\\dir1\\\\dir2\\\\file.zip lastModifiedDate=... ... .. ..:..:.. \\w+ .... size=3 isDirectory=false isFile=true isHidden=false isReadable=true isWritable=true isExecutable=true archiveFormat=zip compressionFormat=gz depth=6",
            "name=file1 path=files\\\\dir1\\\\dir2\\\\file1 type=normal-file absolutePath=\\S+files\\\\dir1\\\\dir2\\\\file1 canonicalPath=\\S+files\\\\dir1\\\\dir2\\\\file1 lastModifiedDate=... ... .. ..:..:.. \\w+ .... size=0 isDirectory=false isFile=true isHidden=false isReadable=true isWritable=true isExecutable=true archiveFormat= compressionFormat= depth=3",
            "name=file3.Z path=files\\\\dir1\\\\dir2\\\\file3.Z type=compressed-file absolutePath=\\S+files\\\\dir1\\\\dir2\\\\file3.Z canonicalPath=\\S+files\\\\dir1\\\\dir2\\\\file3.Z lastModifiedDate=... ... .. ..:..:.. \\w+ .... size=\\d+ isDirectory=false isFile=true isHidden=false isReadable=true isWritable=true isExecutable=true archiveFormat= compressionFormat=gz depth=3",
            "name=file3.Z% path=files\\\\dir1\\\\dir2\\\\file3.Z% type=normal-contents absolutePath=\\S+files\\\\dir1\\\\dir2\\\\file3.Z canonicalPath=\\S+files\\\\dir1\\\\dir2\\\\file3.Z lastModifiedDate=... ... .. ..:..:.. \\w+ .... size=3 isDirectory=false isFile=true isHidden=false isReadable=true isWritable=true isExecutable=true archiveFormat= compressionFormat=gz depth=4"
        );
    }

    @Test public void
    testDescendantsFirst() throws Exception {

        Find find = new Find();
        find.setDescendantsFirst(true);

        FindTest.assertFindOutputMatches(
            find,
            new String[] { "-echo", "path=${path}" },
            "path=files\\\\dir1\\\\dir2\\\\file.zip!/dir1/dir2/file1",
            "path=files\\\\dir1\\\\dir2\\\\file.zip!dir3/dir4/file2",
            "path=files\\\\dir1\\\\dir2\\\\file.zip!dir3/dir4/dir5",
            "path=files\\\\dir1\\\\dir2\\\\file.zip!dir3/dir4/file3.Z%",
            "path=files\\\\dir1\\\\dir2\\\\file.zip!dir3/dir4/file3.Z",
            "path=files\\\\dir1\\\\dir2\\\\file.zip!file.zip!/dir1/dir2/file1",
            "path=files\\\\dir1\\\\dir2\\\\file.zip!file.zip!dir3/dir4/file2",
            "path=files\\\\dir1\\\\dir2\\\\file.zip!file.zip!dir3/dir4/dir5",
            "path=files\\\\dir1\\\\dir2\\\\file.zip!file.zip!dir3/dir4/file3.Z%",
            "path=files\\\\dir1\\\\dir2\\\\file.zip!file.zip!dir3/dir4/file3.Z",
            "path=files\\\\dir1\\\\dir2\\\\file.zip!file.zip",
            "path=files\\\\dir1\\\\dir2\\\\file.zip",
            "path=files\\\\dir1\\\\dir2\\\\file1",
            "path=files\\\\dir1\\\\dir2\\\\file3.Z%",
            "path=files\\\\dir1\\\\dir2\\\\file3.Z",
            "path=files\\\\dir1\\\\dir2",
            "path=files\\\\dir1",
            "path=files"
        );
    }

    @Test public void
    testMinDepth2() throws Exception {

        Find find = new Find();
        find.setMinDepth(2);

        FindTest.assertFindOutputMatches(
            find,
            new String[] { "-echo", "path=${path}" },
//            "path=files",
//            "path=files\\\\dir1",
            "path=files\\\\dir1\\\\dir2",
            "path=files\\\\dir1\\\\dir2\\\\file.zip",
            "path=files\\\\dir1\\\\dir2\\\\file.zip!/dir1/dir2/file1",
            "path=files\\\\dir1\\\\dir2\\\\file.zip!dir3/dir4/file2",
            "path=files\\\\dir1\\\\dir2\\\\file.zip!dir3/dir4/dir5",
            "path=files\\\\dir1\\\\dir2\\\\file.zip!dir3/dir4/file3.Z",
            "path=files\\\\dir1\\\\dir2\\\\file.zip!dir3/dir4/file3.Z%",
            "path=files\\\\dir1\\\\dir2\\\\file.zip!file.zip",
            "path=files\\\\dir1\\\\dir2\\\\file.zip!file.zip!/dir1/dir2/file1",
            "path=files\\\\dir1\\\\dir2\\\\file.zip!file.zip!dir3/dir4/file2",
            "path=files\\\\dir1\\\\dir2\\\\file.zip!file.zip!dir3/dir4/dir5",
            "path=files\\\\dir1\\\\dir2\\\\file.zip!file.zip!dir3/dir4/file3.Z",
            "path=files\\\\dir1\\\\dir2\\\\file.zip!file.zip!dir3/dir4/file3.Z%",
            "path=files\\\\dir1\\\\dir2\\\\file1",
            "path=files\\\\dir1\\\\dir2\\\\file3.Z",
            "path=files\\\\dir1\\\\dir2\\\\file3.Z%"
        );
    }

    @Test public void
    testMinDepth5() throws Exception {

        Find find = new Find();
        find.setMinDepth(5);

        FindTest.assertFindOutputMatches(
            find,
            new String[] { "-echo", "path=${path}" },
//            "path=files",
//            "path=files\\\\dir1",
//            "path=files\\\\dir1\\\\dir2",
//            "path=files\\\\dir1\\\\dir2\\\\file.zip",
//            "path=files\\\\dir1\\\\dir2\\\\file.zip!/dir1/dir2/file1",
//            "path=files\\\\dir1\\\\dir2\\\\file.zip!dir3/dir4/file2",
//            "path=files\\\\dir1\\\\dir2\\\\file.zip!dir3/dir4/dir5",
//            "path=files\\\\dir1\\\\dir2\\\\file.zip!dir3/dir4/file3.Z",
            "path=files\\\\dir1\\\\dir2\\\\file.zip!dir3/dir4/file3.Z%",
//            "path=files\\\\dir1\\\\dir2\\\\file.zip!file.zip",
            "path=files\\\\dir1\\\\dir2\\\\file.zip!file.zip!/dir1/dir2/file1",
            "path=files\\\\dir1\\\\dir2\\\\file.zip!file.zip!dir3/dir4/file2",
            "path=files\\\\dir1\\\\dir2\\\\file.zip!file.zip!dir3/dir4/dir5",
            "path=files\\\\dir1\\\\dir2\\\\file.zip!file.zip!dir3/dir4/file3.Z",
            "path=files\\\\dir1\\\\dir2\\\\file.zip!file.zip!dir3/dir4/file3.Z%"
//            "path=files\\\\dir1\\\\dir2\\\\file1",
//            "path=files\\\\dir1\\\\dir2\\\\file3.Z",
//            "path=files\\\\dir1\\\\dir2\\\\file3.Z%"
        );
    }

    @Test public void
    testMaxDepth2() throws Exception {

        Find find = new Find();
        find.setMaxDepth(2);

        FindTest.assertFindOutputMatches(
            find,
            new String[] { "-echo", "path=${path}" },
            "path=files",
            "path=files\\\\dir1",
            "path=files\\\\dir1\\\\dir2"
//            "path=files\\\\dir1\\\\dir2\\\\file.zip",
//            "path=files\\\\dir1\\\\dir2\\\\file.zip!/dir1/dir2/file1",
//            "path=files\\\\dir1\\\\dir2\\\\file.zip!dir3/dir4/file2",
//            "path=files\\\\dir1\\\\dir2\\\\file.zip!dir3/dir4/dir5",
//            "path=files\\\\dir1\\\\dir2\\\\file.zip!dir3/dir4/file3.Z",
//            "path=files\\\\dir1\\\\dir2\\\\file.zip!dir3/dir4/file3.Z%",
//            "path=files\\\\dir1\\\\dir2\\\\file.zip!file.zip",
//            "path=files\\\\dir1\\\\dir2\\\\file.zip!file.zip!/dir1/dir2/file1",
//            "path=files\\\\dir1\\\\dir2\\\\file.zip!file.zip!dir3/dir4/file2",
//            "path=files\\\\dir1\\\\dir2\\\\file.zip!file.zip!dir3/dir4/dir5",
//            "path=files\\\\dir1\\\\dir2\\\\file.zip!file.zip!dir3/dir4/file3.Z",
//            "path=files\\\\dir1\\\\dir2\\\\file.zip!file.zip!dir3/dir4/file3.Z%",
//            "path=files\\\\dir1\\\\dir2\\\\file1",
//            "path=files\\\\dir1\\\\dir2\\\\file3.Z",
//            "path=files\\\\dir1\\\\dir2\\\\file3.Z%"
        );
    }

    @Test public void
    testMaxDepth4() throws Exception {

        Find find = new Find();
        find.setMaxDepth(4);

        FindTest.assertFindOutputMatches(
            find,
            new String[] { "-echo", "path=${path}" },
            "path=files",
            "path=files\\\\dir1",
            "path=files\\\\dir1\\\\dir2",
            "path=files\\\\dir1\\\\dir2\\\\file.zip",
            "path=files\\\\dir1\\\\dir2\\\\file.zip!/dir1/dir2/file1",
            "path=files\\\\dir1\\\\dir2\\\\file.zip!dir3/dir4/file2",
            "path=files\\\\dir1\\\\dir2\\\\file.zip!dir3/dir4/dir5",
            "path=files\\\\dir1\\\\dir2\\\\file.zip!dir3/dir4/file3.Z",
//            "path=files\\\\dir1\\\\dir2\\\\file.zip!dir3/dir4/file3.Z%",
            "path=files\\\\dir1\\\\dir2\\\\file.zip!file.zip",
//            "path=files\\\\dir1\\\\dir2\\\\file.zip!file.zip!/dir1/dir2/file1",
//            "path=files\\\\dir1\\\\dir2\\\\file.zip!file.zip!dir3/dir4/file2",
//            "path=files\\\\dir1\\\\dir2\\\\file.zip!file.zip!dir3/dir4/dir5",
//            "path=files\\\\dir1\\\\dir2\\\\file.zip!file.zip!dir3/dir4/file3.Z",
//            "path=files\\\\dir1\\\\dir2\\\\file.zip!file.zip!dir3/dir4/file3.Z%",
            "path=files\\\\dir1\\\\dir2\\\\file1",
            "path=files\\\\dir1\\\\dir2\\\\file3.Z",
            "path=files\\\\dir1\\\\dir2\\\\file3.Z%"
        );
    }

    @Test public void
    testDigest() throws Exception {

        // SUPPRESS CHECKSTYLE LineLength|Wrap:6
        FindTest.assertFindOutputMatches(
            new Find(),
            new String[] { "-name", "***file3.Z%", "-print", "-digest", "MD5" },
            "files\\\\dir1\\\\dir2\\\\file.zip!dir3/dir4/file3.Z%",          "250cf8b51c773f3f8dc8b4be867a9a02", // The MD5 of "456"
            "files\\\\dir1\\\\dir2\\\\file.zip!file.zip!dir3/dir4/file3.Z%", "68053af2923e00204c3ca7c6a3150cf7", // The MD5 of "789"
            "files\\\\dir1\\\\dir2\\\\file3.Z%",                             "202cb962ac59075b964b07152d234b70"  // The MD5 of "123"
        );
    }

    @Test public void
    testChecksum() throws Exception {

        // SUPPRESS CHECKSTYLE Wrap:6
        FindTest.assertFindOutputMatches(
            new Find(),
            new String[] { "-name", "***file3.Z%", "-print", "-checksum", "CRC32" },
            "files\\\\dir1\\\\dir2\\\\file.zip!dir3/dir4/file3.Z%",          "b1a8c371", // CRC32 of "456"
            "files\\\\dir1\\\\dir2\\\\file.zip!file.zip!dir3/dir4/file3.Z%", "96ff1ef4", // CRC32 of "789"
            "files\\\\dir1\\\\dir2\\\\file3.Z%",                             "884863d2"  // CRC32 of "123"
        );
    }

    @Test public void
    testEcho() throws Exception {

        FindTest.assertFindOutputMatches(
            new Find(),
            new String[] { "-name", "files", "-echo", "${import java.util.regex.*; Pattern.compile(\"A\")}" },
            "A"
        );
    }

    /**
     * Executes a {@link Find} search with the given <var>expression</var> and asserts that the produced output equals
     * {@code expected}.
     */
    private static void
    assertFindOutputEquals(Find find, String[] expression, String... expected) throws Exception {

        TestCase.assertEquals(Arrays.asList(expected), FindTest.find(find, expression));
    }

    /**
     * Executes a {@link Find} search with the given <var>expression</var> and asserts that the produced output matches
     * the {@code expected} regexes.
     *
     * @param expression The FIND expression, e.g. <code>{ "-echo", "@{path}" }</code>
     */
    private static void
    assertFindOutputMatches(Find find, String[] expression, String... expected) throws Exception {

        AssertRegex.assertMatches(Arrays.asList(expected), FindTest.find(find, expression));
    }

    /**
     * Executes a {@link Find} search with the given <var>expression</var>.
     *
     * @param expression The FIND expression, e.g. <code>{ "-echo", "@{path}" }</code>
     * @return           The lines of output
     */
    private static List<String>
    find(final Find find, String[] expression) throws ParseException, IOException {

        Parser parser = new Parser(ProducerUtil.fromElements(expression), System.out);
        find.setExpression(parser.parse());

        List<String> lines = new ArrayList<String>();
        AbstractPrinter.getContextPrinter().redirectInfo(
            ConsumerUtil.addToCollection(lines)
        ).run(new RunnableWhichThrows<IOException>() {
            @Override public void run() throws IOException { find.findInFile(FindTest.FILES); }
        });

        return lines;
    }
}
