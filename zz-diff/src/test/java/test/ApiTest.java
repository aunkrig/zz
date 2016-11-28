
/*
 * de.unkrig.diff - An advanced version of the UNIX DIFF utility
 *
 * Copyright (c) 2016, Arno Unkrig
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
import java.io.InputStream;
import java.util.Arrays;

import org.apache.tools.ant.filters.StringInputStream;
import org.junit.Assert;
import org.junit.Test;

import de.unkrig.commons.lang.protocol.ProducerWhichThrows;
import de.unkrig.commons.lang.protocol.RunnableWhichThrows;
import de.unkrig.commons.nullanalysis.Nullable;
import de.unkrig.zz.diff.Diff;

/**
 * Tests for the {@link Diff} API.
 */
public
class ApiTest {

    /**
     * Tests the {@link Diff#execute(String, String, ProducerWhichThrows, ProducerWhichThrows)} API.
     */
    @Test public void
    test1() throws IOException {

        RunnableWhichThrows<IOException> runnable = new RunnableWhichThrows<IOException>() {

            @Override public void
            run() throws IOException {
                new Diff().execute(
                    "path1",
                    "path2",
                    ApiTest.opener("---\n---\n---\n---\nDELETED LINE\n---\n---\nCHANGD LINE\n---\n---\n"),
                    ApiTest.opener("---\n---\nADDED LINE\n---\n---\n---\n---\nCHANGED LINE\n---\n---\n")
                );
            }
        };

        Assert.assertEquals(Arrays.asList(new String[] {
            "V: Scanning first stream...",
            "V: Scanning second stream...",
            "V: Computing differences...",
            "V: '' (10 lines) vs. '' (10 lines)",
            "V: 3 raw differences found",
            "V: '' and '' changed",
            "I: 2a3",
            "I: > ADDED LINE",
            "I: 5d5",
            "I: < DELETED LINE",
            "I: 8c8",
            "I: < CHANGD LINE",
            "I: ---",
            "I: > CHANGED LINE",
            "V: 3 differences found.",
        }), AssertPrinters.recordMessages(runnable));
    }

    /**
     * Tests the {@link Diff#diff(String, String, InputStream, InputStream)} API.
     */
    @Test public void
    test2() throws IOException {

        RunnableWhichThrows<IOException> runnable = new RunnableWhichThrows<IOException>() {

            @Override public void
            run() throws IOException {
                new Diff().diff(
                    "/path1",
                    "/path2",
                    new StringInputStream("---\n---\n---\n---\nDELETED LINE\n---\n---\nCHANGD LINE\n---\n---\n"),
                    new StringInputStream("---\n---\nADDED LINE\n---\n---\n---\n---\nCHANGED LINE\n---\n---\n")
                );
            }
        };

        Assert.assertEquals(Arrays.asList(new String[] {
            "V: '/path1' (10 lines) vs. '/path2' (10 lines)",
            "V: 3 raw differences found",
            "I: 2a3",
            "I: > ADDED LINE",
            "I: 5d5",
            "I: < DELETED LINE",
            "I: 8c8",
            "I: < CHANGD LINE",
            "I: ---",
            "I: > CHANGED LINE",
        }), AssertPrinters.recordMessages(runnable));
    }

    private static ProducerWhichThrows<? extends InputStream, IOException>
    opener(final String string) {

        return new ProducerWhichThrows<InputStream, IOException>() {

            @Override @Nullable public InputStream
            produce() { return new ByteArrayInputStream(string.getBytes()); }
        };
    }
}
