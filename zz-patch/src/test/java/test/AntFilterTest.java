
/*
 * de.unkrig.patch - An enhanced version of the UNIX PATCH utility
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

package test;

import java.io.File;

import org.apache.tools.ant.BuildFileTest;
import org.junit.Test;

import de.unkrig.commons.file.FileUtil;
import junit.framework.TestCase;

/**
 * Tests for the 'patch' ANT task.
 */
public
class AntFilterTest extends BuildFileTest {

    private static final File FILES = new File("files");

    @Override public void
    setUp() { this.configureProject("target/test-classes/patch_test.ant"); }

    /***/
    @Test public void
    testFilter1() throws Exception {

        if (AntFilterTest.FILES.exists()) FileUtil.deleteRecursively(AntFilterTest.FILES);

        new Files(new Object[] {
            "old.txt", (
                ""
                + "One\n"
                + "Two\n"
                + "Three\n"
                + "Four\n"
                + "Five\n"
                + "Six\n"
                + "Seven\n"
                + "Eight\n"
                + "Nine\n"
                + "Ten\n"
            ),
            "patch.txt", (
                ""
                + "2a3\n"
                + "> INSERTED\n"
                + "5d5\n"
                + "< Five\n"
                + "7c7\n"
                + "< Seven\n"
                + "---\n"
                + "> SEVEN\n"
            )
        }).save(AntFilterTest.FILES);

        this.executeTarget("testFilter1");

        TestCase.assertNull(
            Files.diff(
                "-",
                Files.loadPlainFile(new File(AntFilterTest.FILES, "new.txt")),
                (
                    ""
                    + "One\n"
                    + "Two\n"
                    + "INSERTED\n"
                    + "Three\n"
                    + "Four\n"
                    + "Six\n"
                    + "SEVEN\n"
                    + "Eight\n"
                    + "Nine\n"
                    + "Ten\n"
                )
            )
        );

        FileUtil.deleteRecursively(AntFilterTest.FILES);
    }

    /***/
    @Test public void
    testRemoveZipEntry1() throws Exception {

        if (AntFilterTest.FILES.exists()) FileUtil.deleteRecursively(AntFilterTest.FILES);

        new Files(new Object[] {
            "dir1", new Object[] {
                "dir2", new Object[] {
                    "file1", "line1\nline2\nline3\n",
                    "file.zip", new Object[] {
                        "/dir1/dir2/file1", "line1\nline2\nline3\n",
                        "dir3/dir4/file2", "line1\nline2\nline3\n",
                        "dir3/dir4/file.zip", new Object[] {
                            "/dir5/dir6/file1", "line1\nline2\nline3\n",
                            "dir7/dir8/file4", "line1\nline2\nline3\n",
                        },
                    },
                },
            },
        }).save(AntFilterTest.FILES);

        this.executeTarget("testRemoveZipEntry1");

        TestCase.assertNull(
            new Files(new Object[] {
                "dir1", new Object[] {
                    "dir2", new Object[] {
                        "file1", "line1\nline2\nline3\n",
                        "file.zip", new Object[] {
                            //"/dir1/dir2/file1", "line1\nline2\nline3\n",
                            "dir3/dir4/file2", "line1\nline2\nline3\n",
                            "dir3/dir4/file.zip", new Object[] {
                                "/dir5/dir6/file1", "line1\nline2\nline3\n",
                                "dir7/dir8/file4", "line1\nline2\nline3\n",
                            },
                        },
                    },
                },
            }).diff(new Files(AntFilterTest.FILES))
        );

        FileUtil.deleteRecursively(AntFilterTest.FILES);
    }

    /***/
    @Test public void
    testRemoveZipEntry2() throws Exception {

        if (AntFilterTest.FILES.exists()) FileUtil.deleteRecursively(AntFilterTest.FILES);

        new Files(new Object[] {
            "dir1", new Object[] {
                "dir2", new Object[] {
                    "file1", "line1\nline2\nline3\n",
                    "file.zip", new Object[] {
                        "/dir1/dir2/file1", "line1\nline2\nline3\n",
                        "dir3/dir4/file2", "line1\nline2\nline3\n",
                        "dir3/dir4/file.zip", new Object[] {
                            "/dir5/dir6/file1", "line1\nline2\nline3\n",
                            "dir7/dir8/file4", "line1\nline2\nline3\n",
                        },
                    },
                },
            },
        }).save(AntFilterTest.FILES);

        this.executeTarget("testRemoveZipEntry2");

        TestCase.assertNull(
            new Files(new Object[] {
                "dir1", new Object[] {
                    "dir2", new Object[] {
                        "file1", "line1\nline2\nline3\n",
                        "file.zip", new Object[] {
                            //"/dir1/dir2/file1", "line1\nline2\nline3\n",
                            "dir3/dir4/file2", "line1\nline2\nline3\n",
                            "dir3/dir4/file.zip", new Object[] {
                                //"/dir5/dir6/file1", "line1\nline2\nline3\n",
                                "dir7/dir8/file4", "line1\nline2\nline3\n",
                            },
                        },
                    },
                },
            }).diff(new Files(AntFilterTest.FILES))
        );

        FileUtil.deleteRecursively(AntFilterTest.FILES);
    }
}
