
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

package test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Test;

import de.unkrig.commons.file.FileUtil;
import de.unkrig.commons.file.contentstransformation.ContentsTransformer;
import de.unkrig.commons.lang.protocol.PredicateUtil;
import de.unkrig.commons.lang.protocol.RunnableWhichThrows;
import de.unkrig.commons.text.pattern.Glob;
import de.unkrig.commons.text.pattern.Pattern2;
import de.unkrig.commons.util.logging.SimpleLogging;
import de.unkrig.zz.patch.Main;
import de.unkrig.zz.patch.Patch;
import de.unkrig.zz.patch.SubstitutionContentsTransformer;
import de.unkrig.zz.patch.SubstitutionContentsTransformer.Condition;

/**
 * Tests for {@link Patch}.
 */
public
class PatchTest {
    private static final File UNPATCHED = new File("files/unpatched");
    private static final File PATCHES   = new File("files/patches");
    private static final File PATCHED   = new File("files/patched");

    /**
     * Verifies that comparing a directory tree with itself yields no DIFFs.
     */
    @Test public void
    archiveFormats() throws Exception {
        if (PatchTest.UNPATCHED.exists()) FileUtil.deleteRecursively(PatchTest.UNPATCHED);
        Files expected = new Files(new Object[] {
            "dir1", new Object[] {
                "dir2", new Object[] {
                    "file1", "line1\nline2\nline3\n",
                    // Creation of 'arj' archives not supported.
//                    "file.arj", new Object[] {
//                        "dir1/dir2/file1", "line1\nline2\nline3\n",
//                        "dir3/dir4/file2", "line1\nline2\nline3\n",
//                    },
                    "file.cpio", new Object[] {
                        "dir1/dir2/file1", "line1\nline2\nline3\n",
                        "dir3/dir4/file2", "line1\nline2\nline3\n",
                    },
                    // Creation of 'dump' archives not supported.
//                    "file.dump", new Object[] {
//                        "dir1/dir2/file1", "line1\nline2\nline3\n",
//                        "dir3/dir4/file2", "line1\nline2\nline3\n",
//                    },
                    "file.jar", new Object[] {
                        "dir1/dir2/file1", "line1\nline2\nline3\n",
                        "dir3/dir4/file2", "line1\nline2\nline3\n",
                    },
                    "file.tar", new Object[] {
                        "dir1/dir2/file1", "line1\nline2\nline3\n",
                        "dir3/dir4/file2", "line1\nline2\nline3\n",
                    },
                    "file.zip", new Object[] {
                        "dir1/dir2/file1", "line1\nline2\nline3\n",
                        "dir3/dir4/file2", "line1\nline2\nline3\n",
                    },
                    "file.7z", new Object[] {
                        "dir1/dir2/file1", "line1\nline2\nline3\n",
                        "dir3/dir4/file2", "line1\nline2\nline3\n",
                    },
                },
            },
        });
        expected.save(PatchTest.UNPATCHED);
        Files actual = new Files(PatchTest.UNPATCHED);
        PatchTest.assertNoDiff(expected, actual);
    }

    /** Pack and unpack .tgz file. */
    @Test public void
    tgz() throws Exception {
        if (PatchTest.UNPATCHED.exists()) FileUtil.deleteRecursively(PatchTest.UNPATCHED);
        Files expected = new Files(new Object[] {
            "dir1", new Object[] {
                "dir2", new Object[] {
                    "file1", "line1\nline2\nline3\n",
                    "file.tgz", new Object[] {
                        "dir1/dir2/file1", "line1\nline2\nline3\n",
                        "dir3/dir4/file2", "line1\nline2\nline3\n",
                    },
                },
            },
        });
        expected.save(PatchTest.UNPATCHED);
        Files actual = new Files(PatchTest.UNPATCHED);
        PatchTest.assertNoDiff(expected, actual);
    }

    /**
     * Verifies that transforming a directory tree with a NOP PATCH command creates an identical tree.
     */
    @Test public void
    nop() throws Exception {
        PatchTest.assertMain(
            new String[] { PatchTest.UNPATCHED.getPath(), PatchTest.PATCHED.getPath() },
            new Files(new Object[] {
                "dir1", new Object[] {
                    "dir2", new Object[] {
                        "file1", "Aline1\nline2\nline3\n",
                        "file.zip", new Object[] {
                            "dir1/dir2/file1", "Bline1\nline2\nline3\n",
                            "dir3/dir4/file2", "Cline1\nline2\nline3\n",
                        },
                    },
                },
            })
        );
    }

    /**
     * Tests the '-substitute' PATCH command.
     */
    @Test public void
    substitute() throws Exception {
        PatchTest.assertMain(
            new String[] {
                "-substitute", "**file1", "line2", "foo",
                "-substitute", "***file1", "line3", "bar",
                PatchTest.UNPATCHED.getPath(),
                PatchTest.PATCHED.getPath()
            },
            new Files(new Object[] {
                "dir1", new Object[] {
                    "dir2", new Object[] {
                        "file1", "Aline1\nfoo\nbar\n",
                        "file.zip", new Object[] {
                            "dir1/dir2/file1", "Bline1\nline2\nbar\n",
                            "dir3/dir4/file2", "Cline1\nline2\nline3\n",
                        },
                    },
                },
            })
        );
    }

    /**
     * Tests complex substitutions across multiple lines.
     */
    @Test public void
    substituteMultilines() throws Exception {

        Patch   patch = new Patch();
        Pattern regex = Pattern.compile("\\s*/\\*[\\s*]*@author\\s+[\\w.]+\\s*\\*/\\s*", Pattern.MULTILINE);
        patch.addContentsTransformation(PredicateUtil.always(), new SubstitutionContentsTransformer(
            Charset.defaultCharset(), // inputCharset
            Charset.defaultCharset(), // outputCharset
            regex,                    // regex
            "\n",                     // replacementString
            Condition.ALWAYS          // condition
        ));

        PatchTest.assertContentsTransformer(
            "aaa\nbbb\n",                                   // expected
            "aaa\n/*\n *\n * @author john.doe\n */\nbbb\n", // in
            patch.contentsTransformer()                     // contentsTransformer
        );
    }

    /**
     * Tests the '-substitute -iff' PATCH command.
     */
    @Test public void
    substituteIff() throws Exception {
        PatchTest.assertMain(
            new String[] {
                "-substitute", "***", "line1", "LINE1", "-iff", "path =* '***dir3**'",
                "-substitute", "***", "line.", "LINE2", "-iff", "match == 'line2'",
                PatchTest.UNPATCHED.getPath(),
                PatchTest.PATCHED.getPath()
            },
            new Files(new Object[] {
                "dir1", new Object[] {
                    "dir2", new Object[] {
                        "file1", "Aline1\nLINE2\nline3\n",
                        "file.zip", new Object[] {
                            "dir1/dir2/file1", "Bline1\nLINE2\nline3\n",
                            "dir3/dir4/file2", "CLINE1\nLINE2\nline3\n",
                        },
                    },
                },
            })
        );
    }

    /**
     * Tests the '-substitute -check-before-transformation' PATCH command.
     */
    @Test public void
    substituteCheckBeforeTransformation() throws Exception {

        if (PatchTest.UNPATCHED.exists()) FileUtil.deleteRecursively(PatchTest.UNPATCHED);
        new Files(new Object[] {
            "dir1", new Object[] {
                "dir2", new Object[] {
                    "file1", "line1\nline2\nline3\n",
                    "file2", "line1\nline2\nline3\n",
                    "file.zip", new Object[] {
                        "dir1/dir2/file1", "line1\nline2\nline3\n",
                        "dir3/dir4/file2", "line1\nline2\nline3\n",
                    },
                },
            },
        }).save(PatchTest.UNPATCHED);

        SimpleLogging.setNormal();

        AssertPrinters.assertContainsMessages(
            new RunnableWhichThrows<RuntimeException>() {

                @Override public void
                run() {
                    Main.main(new String[] {
                        "-check-before-transformation",                         // <==================== !!
                        "-substitute", "**file1", "line2", "foo",
                        "" + "-report", "'R1: ' + path + ', ' + match",
    //                    "" + "-assure", "count == 1",
                        "-substitute", "***file1", "line3", "bar",
                        "" + "-report", "'R2: ' + path + ', ' + match",
    //                    "" + "-assure", "count == 2",
                        PatchTest.UNPATCHED.getPath()
                    });
                }
            },
            PatchTest.c("I: R1: files/unpatched/dir1/dir2/file1, line2"),
            PatchTest.c("I: R2: files/unpatched/dir1/dir2/file1, line3"),
            PatchTest.c("I: R2: files/unpatched/dir1/dir2/file.zip!dir1/dir2/file1, line3")
        );


        Files expected = new Files(new Object[] {
            "dir1", new Object[] {
                "dir2", new Object[] {
                    "file1", "line1\nfoo\nbar\n",
                    "file2", "line1\nline2\nline3\n",
                    "file.zip", new Object[] {
                        "dir1/dir2/file1", "line1\nline2\nbar\n",
                        "dir3/dir4/file2", "line1\nline2\nline3\n",
                    },
                },
            },
        });
        Files actual = new Files(PatchTest.UNPATCHED);
        PatchTest.assertNoDiff(expected, actual);
    }

    /**
     * @return The message, with some occurrences of {@code '/'} replaced with the {@link File#separatorChar}
     */
    private static String
    c(String message) {

        int idx = message.indexOf('!');
        return (
            idx == -1
            ? message.replace('/', File.separatorChar)
            : message.substring(0, idx).replace('/', File.separatorChar) + message.substring(idx)
        );
    }

    /**
     * Tests the '-patch' PATCH command.
     */
    @Test public void
    simplePatch() throws Exception {

        SimpleLogging.setNormal();
        PatchTest.assertPatch(
            "line1\nline2\nline3\n",
            "line1\nLINE2\r\nline3\n",
            (
                ""
                + "--- file1       2013-10-02 08:04:05.999821500 +0200\n"
                + "+++ file2       2013-10-02 08:04:17.980642600 +0200\n"
                + "@@ -1,3 +1,3 @@\n"
                + " line1\n"
                + "-line2\n"
                + "+LINE2\r\n"
                + " line3\n"
            )
        );
    }

    /**
     * Tests the '-patch' PATCH command.
     */
    @Test public void
    contextDiffPatch() throws Exception {

        SimpleLogging.setNormal();
        PatchTest.assertPatch(
            (
                "line1\nline2\nline3\nline4\nline5\n"
                + "line6\nline7\nline8\nline9\nline10\n"
                + "line11\nline12\nline13\nline14\nline15\n"
                + "line16\nline17\n"
            ),
            (
                "line1\nXX\nline2\nline33\nline4\n"
                + "line6\nline7\nline8\nline9\nline10\n"
                + "line11\nline12\nline13\nline144\nline15\n"
                + "line16\nline17\n"
            ),
            (
                ""
                + "*** file1       2013-10-02 11:03:00.290883300 +0200\n"
                + "--- file2       2013-10-02 11:03:29.665734900 +0200\n"
                + "***************\n"
                + "*** 1,8 ****\n"
                + "  line1\n"
                + "  line2\n"
                + "! line3\n"
                + "  line4\n"
                + "- line5\n"
                + "  line6\n"
                + "  line7\n"
                + "  line8\n"
                + "--- 1,8 ----\n"
                + "  line1\n"
                + "+ XX\n"
                + "  line2\n"
                + "! line33\n"
                + "  line4\n"
                + "  line6\n"
                + "  line7\n"
                + "  line8\n"
                + "***************\n"
                + "*** 11,17 ****\n"
                + "  line11\n"
                + "  line12\n"
                + "  line13\n"
                + "! line14\n"
                + "  line15\n"
                + "  line16\n"
                + "  line17\n"
                + "--- 11,17 ----\n"
                + "  line11\n"
                + "  line12\n"
                + "  line13\n"
                + "! line144\n"
                + "  line15\n"
                + "  line16\n"
                + "  line17\n"
            )
        );
    }

    /**
     * Tests the '-patch' PATCH command.
     */
    @Test public void
    traditionalDiffPatch() throws Exception {

        SimpleLogging.setNormal();
        PatchTest.assertPatch(
            (
                "line1\nline2\nline3\nline4\nline5\n"
                + "line6\nline7\nline8\nline9\nline10\n"
                + "line11\nline12\nline13\nline14\nline15\n"
                + "line16\nline17\n"
            ),
            (
                "line1\nXX\nline2\nline33\nline4\n"
                + "line6\nline7\nline8\nline9\nline10\n"
                + "line11\nline12\nline13\nline144\nline15\n"
                + "line16\nline17\n"
            ),
            (
                ""
                + "1a2\n"
                + "> XX\n"
                + "3c4\n"
                + "< line3\n"
                + "---\n"
                + "> line33\n"
                + "5d5\n"
                + "< line5\n"
                + "14c14\n"
                + "< line14\n"
                + "---\n"
                + "> line144\n"
            )
        );
    }

    /**
     * Tests the '-patch' PATCH command.
     */
    @Test public void
    unifiedDiffPatch() throws Exception {

        SimpleLogging.setNormal();
        PatchTest.assertPatch(
            (
                "line1\nline2\nline3\nline4\nline5\n"
                + "line6\nline7\nline8\nline9\nline10\n"
                + "line11\nline12\nline13\nline14\nline15\n"
                + "line16\nline17\n"
            ),
            (
                "line1\nXX\nline2\nline33\nline4\n"
                + "line6\nline7\nline8\nline9\nline10\n"
                + "line11\nline12\nline13\nline144\nline15\n"
                + "line16\nline17\n"
            ),
            (
                ""
                + "--- file1       2013-10-02 11:03:00.290883300 +0200\n"
                + "+++ file2       2013-10-02 11:03:29.665734900 +0200\n"
                + "@@ -1,8 +1,8 @@\n"
                + " line1\n"
                + "+XX\n"
                + " line2\n"
                + "-line3\n"
                + "+line33\n"
                + " line4\n"
                + "-line5\n"
                + " line6\n"
                + " line7\n"
                + " line8\n"
                + "@@ -11,7 +11,7 @@\n"
                + " line11\n"
                + " line12\n"
                + " line13\n"
                + "-line14\n"
                + "+line144\n"
                + " line15\n"
                + " line16\n"
                + " line17\n"
            )
        );
    }

    /**
     * Tests the '-rename' PATCH command.
     */
    @Test public void
    rename() throws Exception {
        PatchTest.assertMain(
            new String[] {

                // Rename file 'dir1/dir2/file1' twice:
                "-rename", "(files/unpatched/dir1/dir2)/file1=$1/file11",
                "-rename", "(files/unpatched/dir1/dir2)/file11=$1/file33",

                // Rename archive entry 'dir1/dir2/file.zip!dir1/dir2/file1' twice:
                "-rename", "(files/unpatched/dir1/dir2/file.zip)!**/dir2/file1=$1!dir11/file11",
                "-rename", "(files/unpatched/dir1/dir2/file.zip)!**/file11=$1!dir1/file22",
                PatchTest.UNPATCHED.getPath(),
                PatchTest.PATCHED.getPath()
            },
            new Files(new Object[] {
                "dir1", new Object[] {
                    "dir2", new Object[] {
                        "file.zip", new Object[] {
                            "dir1/file22",     "Bline1\nline2\nline3\n",
                            "dir3/dir4/file2", "Cline1\nline2\nline3\n",
                        },
                        "file33", "Aline1\nline2\nline3\n",
                    },
                },
            })
        );
    }

    /**
     * Tests the '-update' PATCH command.
     */
    @Test public void
    update() throws Exception {
        String prefix = PatchTest.UNPATCHED.getPath().replace('\\', '/');
        PatchTest.assertMain(
            new String[] {

                // Update file 'dir1/dir2/file.zip!dir3/dir4/file2' from 'dir1/dir2/file1':
                "-update", prefix + "/dir1/dir2/file.zip!dir3/dir4/file2=" + prefix + "/dir1/dir2/file1",
                "-debug",

                PatchTest.UNPATCHED.getPath(),
                PatchTest.PATCHED.getPath()
            },
            new Files(new Object[] {
                "dir1", new Object[] {
                    "dir2", new Object[] {
                        "file.zip", new Object[] {
                            "dir1/dir2/file1", "Bline1\nline2\nline3\n",
                            "dir3/dir4/file2", "Aline1\nline2\nline3\n",
                        },
                        "file1", "Aline1\nline2\nline3\n",
                    },
                },
            })
        );
    }

    /**
     * Tests the '-add' PATCH command.
     */
    @Test public void
    add() throws Exception {
        PatchTest.assertMain(new String[] {
            "-add", "**1",         "newfile",         PatchTest.UNPATCHED + "/dir1/dir2/file1",
            "-add", "***file.zip", "dir/dir/newfile", PatchTest.UNPATCHED + "/dir1/dir2/file1",
            PatchTest.UNPATCHED.getPath(),
            PatchTest.PATCHED.getPath()
        }, new Files(new Object[] {
            "dir1", new Object[] {
                "newfile", "Aline1\nline2\nline3\n",
                "dir2", new Object[] {
                    "file1", "Aline1\nline2\nline3\n",
                    "file.zip", new Object[] {
                        "dir1/dir2/file1", "Bline1\nline2\nline3\n",
                        "dir3/dir4/file2", "Cline1\nline2\nline3\n",
                        "dir/dir/newfile", "Aline1\nline2\nline3\n",
                    },
                },
            },
        }));
    }

    /**
     * Tests the '-add' PATCH command.
     */
    @Test public void
    addContents() throws Exception {
        if (PatchTest.UNPATCHED.exists()) FileUtil.deleteRecursively(PatchTest.UNPATCHED);
        new Files(new Object[] {
            "file.zip", new Object[] {
                "dir1/dir2/file3", "line1\nline2\nline3\n",
                "dir4/dir5/file6", "line1\nline2\nline3\n",
                "dir7/dir8/nested.zip", new Object[] {
                    "dir9/dir10/file11",  "line1\nline2\nline3\n",
                    "dir12/dir13/file14", "line1\nline2\nline3\n",
                },
            },
            "newfile", "new line1\nnew line2\nnew line3\n",
        }).save(PatchTest.UNPATCHED);

        Patch patch = new Patch();
        patch.addAddition(
            Glob.compile("-,***.zip", Pattern2.WILDCARD | Glob.INCLUDES_EXCLUDES),
            "newfile",
            new File(PatchTest.UNPATCHED, "newfile")
        );

        if (PatchTest.PATCHED.exists()) FileUtil.deleteRecursively(PatchTest.PATCHED);
        Assert.assertTrue(PatchTest.PATCHED.mkdirs());

        SimpleLogging.setNormal();

        OutputStream os = new FileOutputStream(new File(PatchTest.PATCHED, "patched.zip"));
        try {
            FileInputStream is = new FileInputStream(new File(PatchTest.UNPATCHED, "file.zip"));
            try {
                patch.contentsTransformer().transform("-", is, os);
            } finally {
                is.close();
            }
        } finally {
            os.close();
        }

        Files expected = new Files(new Object[] {
            "patched.zip", new Object[] {
                "dir1/dir2/file3", "line1\nline2\nline3\n",
                "dir4/dir5/file6", "line1\nline2\nline3\n",
                "newfile",         "new line1\nnew line2\nnew line3\n",
                "dir7/dir8/nested.zip", new Object[] {
                    "dir9/dir10/file11",  "line1\nline2\nline3\n",
                    "dir12/dir13/file14", "line1\nline2\nline3\n",
                    "newfile",            "new line1\nnew line2\nnew line3\n",
                },
            },
        });
        Files actual = new Files(PatchTest.PATCHED);
        PatchTest.assertNoDiff(expected, actual);
    }

    /**
     * Creates a file tree like
     * <pre>
     * dir1/dir2/file1:                    Aline1\nline2\nline3\n
     * dir1/dir2/file.zip!dir1/dir2/file1: Bline1\nline2\nline3\n
     * dir1/dir2/file.zip!dir3/dir4/file2: Cline1\nline2\nline3\n
     * </pre>
     * , executes {@link Main#main(String[])} and asserts that the result equals {@code expected}.
     */
    private static void
    assertMain(String[] args, Files expected) throws Exception {

        if (PatchTest.UNPATCHED.exists()) FileUtil.deleteRecursively(PatchTest.UNPATCHED);
        new Files(new Object[] {
            "dir1", new Object[] {
                "dir2", new Object[] {
                    "file1", "Aline1\nline2\nline3\n",
                    "file.zip", new Object[] {
                        "dir1/dir2/file1", "Bline1\nline2\nline3\n",
                        "dir3/dir4/file2", "Cline1\nline2\nline3\n",
                    },
                },
            },
        }).save(PatchTest.UNPATCHED);

        if (PatchTest.PATCHED.exists()) FileUtil.deleteRecursively(PatchTest.PATCHED);

        SimpleLogging.setNormal();
        Main.main(args);

        Files actual = new Files(PatchTest.PATCHED);
        PatchTest.assertNoDiff(expected, actual);
    }

    private static void
    assertPatch(String unpatchedContents, String patchedContents, String patch) throws IOException, Exception {
        if (PatchTest.UNPATCHED.exists()) FileUtil.deleteRecursively(PatchTest.UNPATCHED);
        new Files(new Object[] {
            "dir1", new Object[] {
                "dir2", new Object[] {
                    "file.zip", new Object[] {
                        "dir1/dir2/file1", unpatchedContents,
                    },
                },
            },
        }).save(PatchTest.UNPATCHED);

        if (PatchTest.PATCHES.exists()) FileUtil.deleteRecursively(PatchTest.PATCHES);
        new Files(new Object[] {
            "patch.txt", patch,
        }).save(PatchTest.PATCHES);

        if (PatchTest.PATCHED.exists()) FileUtil.deleteRecursively(PatchTest.PATCHED);
        Main.main(new String[] {
            "-patch", "***file1", new File(PatchTest.PATCHES, "patch.txt").getPath(),
            PatchTest.UNPATCHED.getPath(),
            PatchTest.PATCHED.getPath()
        });

        Files expected = new Files(new Object[] {
            "dir1", new Object[] {
                "dir2", new Object[] {
                    "file.zip", new Object[] {
                        "dir1/dir2/file1", patchedContents,
                    },
                },
            },
        });
        Files actual = new Files(PatchTest.PATCHED);
        PatchTest.assertNoDiff(expected, actual);
    }

    private static void
    assertNoDiff(Files expected, Files actual) {
        String diff = expected.diff(actual);
        if (diff != null) Assert.fail(diff);
    }

    /**
     * Asserts that the <var>contentsTransformer</var> transforms the <var>in</var> string into <var>expected</var>.
     */
    private static void
    assertContentsTransformer(String expected, String in, ContentsTransformer contentsTransformer) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        contentsTransformer.transform("", new ByteArrayInputStream(in.getBytes()), baos);

        Assert.assertEquals(expected, new String(baos.toByteArray()));
    }
}
