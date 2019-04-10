
/*
 * de.unkrig.diff - An advanced version of the UNIX DIFF utility
 *
 * Copyright (c) 2019, Arno Unkrig
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
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Test;

import de.unkrig.commons.lang.protocol.PredicateUtil;
import de.unkrig.commons.lang.protocol.RunnableWhichThrows;
import de.unkrig.zz.diff.DocumentDiff;
import de.unkrig.zz.diff.DocumentDiff.LineEquivalence;

public
class DocumentDiffTest {

    @Test public void
    test1() throws IOException {

        DocumentDiff documentDiff = new DocumentDiff();
        DocumentDiffTest.assertDiffEquals(new String[] {
            "V: '' (1 line) vs. '' (1 line)",
            "V: 1 raw difference found",
            "I: 1c1",
            "I: < minVersion=1.2",
            "I: ---",
            "I: > minVersion=1.3",
        }, documentDiff);

        DocumentDiffTest.assertNumberOfDifferencesEquals(1, documentDiff);
    }

    @Test public void
    test2() throws IOException {

        DocumentDiff documentDiff = new DocumentDiff();
        documentDiff.addEquivalentLine(new LineEquivalence(PredicateUtil.always(), Pattern.compile("\\d+")));

        DocumentDiffTest.assertNoDifferences(documentDiff);
    }

    /**
     * Test capturing groups.
     */
    @Test public void
    test3() throws IOException {

        DocumentDiff documentDiff = new DocumentDiff();
        documentDiff.addEquivalentLine(
            new LineEquivalence(PredicateUtil.always(), Pattern.compile("minVersion=(\\d+(?:\\.\\d+)*)"))
        );
        DocumentDiffTest.assertNoDifferences(documentDiff);

        documentDiff = new DocumentDiff();
        documentDiff.addEquivalentLine(
            new LineEquivalence(PredicateUtil.always(), Pattern.compile("minVersion=(\\d+)\\.\\d+"))
        );
        DocumentDiffTest.assertNumberOfDifferencesEquals(1, documentDiff);

        documentDiff = new DocumentDiff();
        documentDiff.addEquivalentLine(
            new LineEquivalence(PredicateUtil.always(), Pattern.compile("minVersion=\\d+\\.(\\d+)"))
        );
        DocumentDiffTest.assertNoDifferences(documentDiff);

        documentDiff = new DocumentDiff();
        documentDiff.addEquivalentLine(
            new LineEquivalence(PredicateUtil.always(), Pattern.compile("minVersion=(\\d+)\\.(\\d+)"))
        );
        DocumentDiffTest.assertNoDifferences(documentDiff);
    }

    /**
     * Test nested capturing groups.
     */
    @Test public void
    test4() throws IOException {

        DocumentDiff documentDiff = new DocumentDiff();
        documentDiff.addEquivalentLine(
            new LineEquivalence(PredicateUtil.always(), Pattern.compile("minVersion=(\\d+(\\.\\d+)*)"))
        );

        DocumentDiffTest.assertNoDifferences(documentDiff);
    }

    private static void
    assertNoDifferences(DocumentDiff documentDiff) throws IOException {
        DocumentDiffTest.assertNumberOfDifferencesEquals(0, documentDiff);
    }

    private static void
    assertNumberOfDifferencesEquals(int expected, DocumentDiff documentDiff) throws IOException {

        List<String> messages = DocumentDiffTest.runDiff(documentDiff);

        int actual;
        GET_NUMBER_OF_DIFFERENCES:
        {
            for (String message : messages) {
                Matcher matcher = DocumentDiffTest.NUMBER_OF_RAW_DIFFERENCES_MESSAGE_PATTERN.matcher(message);
                if (matcher.matches()) {
                    actual = Integer.parseInt(matcher.group(1));
                    break GET_NUMBER_OF_DIFFERENCES;
                }
            }
            Assert.fail(messages.toString());
            return;
        }

        Assert.assertEquals("Number of differences:", expected, actual);
    }
    private static final Pattern
    NUMBER_OF_RAW_DIFFERENCES_MESSAGE_PATTERN = Pattern.compile("V: (\\d+) raw differences? found");

    private static void
    assertDiffEquals(String[] expected, final DocumentDiff documentDiff) throws IOException {
        Assert.assertEquals(Arrays.asList(expected), DocumentDiffTest.runDiff(documentDiff));
    }

    /**
     * @return The messages reported by {@link DocumentDiff#diff(String, String, java.io.InputStream,
     *         java.io.InputStream)}
     */
    private static List<String>
    runDiff(final DocumentDiff documentDiff) throws IOException {
        return AssertPrinters.recordMessages(new RunnableWhichThrows<IOException>() {

            @Override public void
            run() throws IOException {
                documentDiff.diff(
                    "",                                                    // path1
                    "",                                                    // path2
                    new ByteArrayInputStream("minVersion=1.2".getBytes()), // stream1
                    new ByteArrayInputStream("minVersion=1.3".getBytes())  // stream2
                );
            }
        });
    }
}
