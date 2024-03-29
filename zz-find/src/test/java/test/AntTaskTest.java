
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

package test;

import java.io.File;
import java.io.IOException;

import org.apache.tools.ant.BuildFileTest;
import org.junit.Test;

import de.unkrig.commons.file.FileUtil;

/**
 * Tests for the 'find' ANT task.
 */
public
class AntTaskTest extends BuildFileTest {

    private static final File FILES = new File("files").getAbsoluteFile();

    @Override public void
    setUp() throws IOException {

        // The BASEDIR defaults to the build script's directory, which is not what we want.
        System.setProperty("basedir", System.getProperty("user.dir"));

        this.configureProject("src/test/resources/find_test.ant");

        if (AntTaskTest.FILES.exists()) FileUtil.deleteRecursively(AntTaskTest.FILES);
        new Files(new Object[] {
            "dir1", new Object[] {
                "dir2", new Object[] {
                    "file1",    "line1\nline2\nline3\n",
                    "file.zip", new Object[] {
                        "/dir1/dir2/file1",   "line1\nline2\nline3\n",
                        "dir3/dir4/file2",    "line1\nline2\nline3\n",
                        "dir3/dir4/file.zip", new Object[] {
                            "/dir5/dir6/file1", "line1\nline2\nline3\n",
                            "dir7/dir8/file4",  "line1\nline2\nline3\n",
                        },
                    },
                },
            },
        }).save(AntTaskTest.FILES);
    }

    @Override protected void
    tearDown() throws Exception {
        FileUtil.deleteRecursively(AntTaskTest.FILES);
    }

    /**
     * Creates a directory tree, runs an ANT script containing the FIND and verifies that the expected files are found.
     */
    @Test public void
    test1() {

        this.expectLog("find1", (
            new File(AntTaskTest.FILES, "dir1/dir2/file.zip").getPath() + "!/dir1/dir2/file1"
            + new File(AntTaskTest.FILES, "dir1/dir2/file1").getPath()
        ));
    }

    /**
     * Creates a directory tree, runs an ANT script containing the FIND and verifies that the expected files are found.
     */
    @Test public void
    test2() {

        this.expectLog("find2", (
            new File(AntTaskTest.FILES, "dir1/dir2/file.zip").getPath() + "!/dir1/dir2/file1"
            + new File(AntTaskTest.FILES, "dir1/dir2/file.zip").getPath() + "!dir3/dir4/file.zip!/dir5/dir6/file1" // SUPPRESS CHECKSTYLE LineLength
            + new File(AntTaskTest.FILES, "dir1/dir2/file1").getPath()
        ));
    }
}
