
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

import org.apache.tools.ant.BuildFileTest;
import org.junit.Test;

import de.unkrig.commons.file.FileUtil;
import junit.framework.TestCase;

/**
 * Tests for the 'patch' ANT task.
 */
public
class AntTaskTest extends BuildFileTest {

    private static final File FILES = new File("files");

    @Override public void
    setUp() { this.configureProject("target/test-classes/patch_test.ant"); }

    /***/
    @Test public void
    testRemoveFile1() throws Exception {
        this.testRemoveFileX(1);
    }

    /***/
    @Test public void
    testRemoveFile2() throws Exception {
        this.testRemoveFileX(2);
    }

    private void
    testRemoveFileX(int i) throws Exception {

        if (AntTaskTest.FILES.exists()) FileUtil.deleteRecursively(AntTaskTest.FILES);

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
        }).save(AntTaskTest.FILES);

        this.executeTarget("testRemoveFile" + i);

        TestCase.assertNull(
            new Files(new Object[] {
                "dir1", new Object[] {
                    "dir2", new Object[] {
                        //"file1", "line1\nline2\nline3\n",
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
            }).diff(new Files(AntTaskTest.FILES))
        );

        FileUtil.deleteRecursively(AntTaskTest.FILES);
    }

    /***/
    @Test public void
    testRemoveZipEntry1() throws Exception {

        if (AntTaskTest.FILES.exists()) FileUtil.deleteRecursively(AntTaskTest.FILES);

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
        }).save(AntTaskTest.FILES);

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
            }).diff(new Files(AntTaskTest.FILES))
        );

        FileUtil.deleteRecursively(AntTaskTest.FILES);
    }

    /***/
    @Test public void
    testRemoveZipEntry2() throws Exception {

        if (AntTaskTest.FILES.exists()) FileUtil.deleteRecursively(AntTaskTest.FILES);

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
        }).save(AntTaskTest.FILES);

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
            }).diff(new Files(AntTaskTest.FILES))
        );

        FileUtil.deleteRecursively(AntTaskTest.FILES);
    }

    /***/
    @Test public void
    testPatch1() throws Exception {

        if (AntTaskTest.FILES.exists()) FileUtil.deleteRecursively(AntTaskTest.FILES);

        new Files(new Object[] { "file.txt", "xxxAAAxxx" }).save(AntTaskTest.FILES);

        this.executeTarget("testPatch1");

        TestCase.assertNull(
            new Files(new Object[] { "file.txt", "xxxBBBxxx" }).diff(new Files(AntTaskTest.FILES))
        );

        FileUtil.deleteRecursively(AntTaskTest.FILES);
    }

    /***/
    @Test public void
    testPatch2() throws Exception {

        if (AntTaskTest.FILES.exists()) FileUtil.deleteRecursively(AntTaskTest.FILES);

        new Files(new Object[] { "file.txt", (
            ""
            + "   <filter>\n"
            + "      <!--\n"
            + "         bla\n"
            + "      -->\n"
            + "      <!--\n"
            + "         balbla\n"
            + "      -->\n"
            + "      <filter-name>CorsFilter</filter-name>\n"
            + "      <filter-class>org.apache.catalina.filters.CorsFilter</filter-class>\n"
            + "      <init-param>\n"
            + "      </init-param>\n"
            + "   </filter>\n"
        ) }).save(AntTaskTest.FILES);

        this.executeTarget("testPatch2");

        TestCase.assertNull(
            new Files(new Object[] { "file.txt", "" }).diff(new Files(AntTaskTest.FILES))
        );

        FileUtil.deleteRecursively(AntTaskTest.FILES);
    }
}
