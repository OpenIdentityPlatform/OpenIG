/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2010–2011 ApexIdentity Inc.
 * Portions Copyright 2011-2014 ForgeRock AS.
 */

package org.forgerock.http.io;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

import org.forgerock.util.Factory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class BranchingStreamWrapperTest {

    /** The size of a read buffer. */
    private static final int BUFSIZE = 8192;

    /** Number of concurrent branches to test against. */
    private static final int BRANCHES = 17; // prime number of branches

    /** Read lengths, randomly generated and hard-coded here for deterministic results. */
    private static final int[] LENGTHS = {
            // @Checkstyle:ignoreFor 3
            125, 42, 185, 249, 177, 74, 98, 124,
            42, 160, 73, 176, 41, 110, 43, 2, 179, 151, 115, 117, 235, 15, 80, 219,
            91, 38, 191, 154, 78, 146, 194
    }; // prime number of lengths

    /** Original bytes to use to read from branches. */
    private static final byte[] BYTES = new byte[BUFSIZE];

    /** Buffer factory, which will switch from memory to file storage in each test. */
    private static final Factory<Buffer> STORAGE = IO.newTemporaryStorage();

    /** Stores content read in concurrent branches for comparison. */
    private static final byte[][] BUFFERS = new byte[BRANCHES][BUFSIZE];

    /** Current read length offset. */
    private static int currentLengthCounter = 0;

    private BranchingStreamWrapper trunk;

    private int nextReadLength() {
        return LENGTHS[(currentLengthCounter++) % LENGTHS.length];
    }

    // ----- context -----------------------------------------------------------

    @BeforeClass
    public void beforeClass() {
        new Random().nextBytes(BYTES); // fill bytes with random data
    }

    @BeforeMethod
    public void beforeMethod() {
        ByteArrayInputStream in = new ByteArrayInputStream(BYTES);
        trunk = new BranchingStreamWrapper(in, STORAGE);
        for (int n = 0; n < BRANCHES; n++) {
            Arrays.fill(BUFFERS[n], (byte) 0);
        }
    }

    @AfterMethod
    public void afterMethod() throws IOException {
        trunk.close();
    }

    // ----- no branch ---------------------------------------------------------

    @Test
    public void noBranchByteAtATime() throws IOException {
        for (int n = 0; n < BUFSIZE; n++) {
            assertThat(trunk.read()).isEqualTo(BYTES[n] & 0xff); // identical content
        }
        assertThat(trunk.read()).isEqualTo(-1); // end of stream
    }

    @Test
    public void noBranchAllBytes() throws IOException {
        assertThat(trunk.read(BUFFERS[0])).isEqualTo(BUFSIZE); // correct read length
        assertThat(BUFFERS[0]).isEqualTo(BYTES); // identical content
        assertThat(trunk.read(BUFFERS[0])).isEqualTo(-1); // end of stream
    }

    @Test
    public void noBranchBytesLength() throws IOException {
        assertThat(trunk.read(BUFFERS[0], 0, BUFSIZE)).isEqualTo(BUFSIZE); // correct read length
        assertThat(BUFFERS[0]).isEqualTo(BYTES); // identical content
        assertThat(trunk.read(BUFFERS[0], 0, BUFSIZE)).isEqualTo(-1); // end of stream
    }

    @Test
    public void noBranchTwoReads() throws IOException {
        int length1 = BUFSIZE / 2;
        int length2 = BUFSIZE - length1;
        assertThat(trunk.read(BUFFERS[0], 0, length1)).isEqualTo(length1); // correct read length
        assertThat(trunk.read(BUFFERS[0], length1, length2)).isEqualTo(length2); // correct read length
        assertThat(BUFFERS[0]).isEqualTo(BYTES); // identical content
        assertThat(trunk.read()).isEqualTo(-1); // end of stream
    }

    // ----- one branch, sequential reads --------------------------------------

    @Test
    public void oneBranchSequentialByteAtATime() throws IOException {
        BranchingStreamWrapper branch = trunk.branch();
        for (int n = 0; n < BUFSIZE; n++) {
            assertThat(trunk.read()).isEqualTo(BYTES[n] & 0xff); // identical content
        }
        assertThat(trunk.read()).isEqualTo(-1); // end of stream
        for (int n = 0; n < BUFSIZE; n++) {
            assertThat(branch.read()).isEqualTo(BYTES[n] & 0xff); // identical content
        }
        assertThat(branch.read()).isEqualTo(-1); // end of stream
        branch.close();
    }

    @Test
    public void oneBranchSequentialAllBytes() throws IOException {
        BranchingStreamWrapper branch = trunk.branch();
        assertThat(trunk.read(BUFFERS[0])).isEqualTo(BUFSIZE); // correct read length
        assertThat(BUFFERS[0]).isEqualTo(BYTES); // identical content
        assertThat(trunk.read(BUFFERS[0])).isEqualTo(-1); // end of stream
        assertThat(branch.read(BUFFERS[1])).isEqualTo(BUFSIZE); // correct read length
        assertThat(BUFFERS[1]).isEqualTo(BYTES); // identical content
        assertThat(branch.read(BUFFERS[1])).isEqualTo(-1); // end of stream
        branch.close();
    }

    @Test
    public void oneBranchSequentialBytesLength() throws IOException {
        BranchingStreamWrapper branch = trunk.branch();
        assertThat(trunk.read(BUFFERS[0], 0, BUFSIZE)).isEqualTo(BUFSIZE); // correct read length
        assertThat(BUFFERS[0]).isEqualTo(BYTES); // identical content
        assertThat(trunk.read(BUFFERS[0], 0, BUFSIZE)).isEqualTo(-1); // end of stream
        assertThat(branch.read(BUFFERS[0], 0, BUFSIZE)).isEqualTo(BUFSIZE); // correct read length
        assertThat(BUFFERS[0]).isEqualTo(BYTES); // identical content
        assertThat(branch.read(BUFFERS[0], 0, BUFSIZE)).isEqualTo(-1); // end of stream
        branch.close();
    }

    @Test
    public void oneBranchSequentialTwoReads() throws IOException {
        int length1 = BUFSIZE / 2;
        int length2 = BUFSIZE - length1;
        BranchingStreamWrapper branch = trunk.branch();
        assertThat(trunk.read(BUFFERS[0], 0, length1)).isEqualTo(length1); // correct read length
        assertThat(trunk.read(BUFFERS[0], length1, length2)).isEqualTo(length2); // correct read length
        assertThat(BUFFERS[0]).isEqualTo(BYTES); // identical content
        assertThat(trunk.read()).isEqualTo(-1); // end of stream
        assertThat(branch.read(BUFFERS[1], 0, length1)).isEqualTo(length1); // correct read length
        assertThat(branch.read(BUFFERS[1], length1, length2)).isEqualTo(length2); // correct read length
        assertThat(BUFFERS[1]).isEqualTo(BYTES); // identical content
        assertThat(branch.read()).isEqualTo(-1); // end of stream
        branch.close();
    }

    // ----- one branch, interleaved reads -------------------------------------

    @Test
    public void oneBranchInterleavedByteAtATime() throws IOException {
        BranchingStreamWrapper branch = trunk.branch();
        for (int n = 0; n < BUFSIZE; n++) {
            assertThat(trunk.read()).isEqualTo(BYTES[n] & 0xff); // identical content
            assertThat(branch.read()).isEqualTo(BYTES[n] & 0xff); // identical content
        }
        assertThat(trunk.read()).isEqualTo(-1); // end of stream
        assertThat(branch.read()).isEqualTo(-1); // end of stream
        branch.close();
    }

    @Test
    public void oneBranchInterleavedAllBytes() throws IOException {
        BranchingStreamWrapper branch = trunk.branch();
        assertThat(trunk.read(BUFFERS[0])).isEqualTo(BUFSIZE); // correct read length
        assertThat(branch.read(BUFFERS[1])).isEqualTo(BUFSIZE); // correct read length
        assertThat(BUFFERS[0]).isEqualTo(BYTES); // identical content
        assertThat(BUFFERS[1]).isEqualTo(BYTES); // identical content
        assertThat(trunk.read(BUFFERS[0])).isEqualTo(-1); // end of stream
        assertThat(branch.read(BUFFERS[1])).isEqualTo(-1); // end of stream
        branch.close();
    }

    @Test
    public void oneBranchInterleavedBytesLength() throws IOException {
        BranchingStreamWrapper branch = trunk.branch();
        assertThat(trunk.read(BUFFERS[0], 0, BUFSIZE)).isEqualTo(BUFSIZE); // correct read length
        assertThat(branch.read(BUFFERS[0], 0, BUFSIZE)).isEqualTo(BUFSIZE); // correct read length
        assertThat(BUFFERS[0]).isEqualTo(BYTES); // identical content
        assertThat(BUFFERS[0]).isEqualTo(BYTES); // identical content
        assertThat(trunk.read(BUFFERS[0], 0, BUFSIZE)).isEqualTo(-1); // end of stream
        assertThat(branch.read(BUFFERS[0], 0, BUFSIZE)).isEqualTo(-1); // end of stream
        branch.close();
    }

    @Test
    public void oneBranchInterleavedTwoReads() throws IOException {
        int length1 = BUFSIZE / 2;
        int length2 = BUFSIZE - length1;
        BranchingStreamWrapper branch = trunk.branch();
        assertThat(trunk.read(BUFFERS[0], 0, length1)).isEqualTo(length1); // correct read length
        assertThat(branch.read(BUFFERS[1], 0, length1)).isEqualTo(length1); // correct read length
        assertThat(trunk.read(BUFFERS[0], length1, length2)).isEqualTo(length2); // correct read length
        assertThat(branch.read(BUFFERS[1], length1, length2)).isEqualTo(length2); // correct read length
        assertThat(BUFFERS[0]).isEqualTo(BYTES); // identical content
        assertThat(BUFFERS[1]).isEqualTo(BYTES); // identical content
        assertThat(trunk.read()).isEqualTo(-1); // end of stream
        assertThat(branch.read()).isEqualTo(-1); // end of stream
        branch.close();
    }

    // ----- n branches, interleaved reads -------------------------------------

    @Test
    public void nBranchesReadInterleavedAllFromTrunk() throws IOException {
        int[] offsets = new int[BRANCHES];
        BranchingStreamWrapper[] branches = new BranchingStreamWrapper[BRANCHES];
        for (int b = 0; b < BRANCHES; b++) { // all branch at the beginning
            branches[b] = trunk.branch();
        }
        boolean still = true;
        while (still) {
            still = false; // start assuming there is no work done
            for (int b = 0; b < BRANCHES; b++) {
                if (offsets[b] < BUFSIZE) {
                    still = true; // there is work done, so loop again
                    int length = branches[b].read(BUFFERS[b], 0, nextReadLength());
                    assertThat(length).isPositive();
                    assertThat(Arrays.copyOfRange(BUFFERS[b], 0, length))
                            .isEqualTo(Arrays.copyOfRange(BYTES, offsets[b], offsets[b] + length));
                    offsets[b] += length;
                } else {
                    assertThat(branches[b].read() < 0);
                }
            }
        }
        for (int b = 0; b < BRANCHES; b++) {
            assertThat(branches[b].read() == -1);
        }
        // purposely leave branches open to let trunk close them recursively
    }

    @Test
    public void nBranchesReadInterleavedBranchAndCloseMany() throws IOException {
        int[] offsets = new int[BRANCHES];
        BranchingStreamWrapper[] branches = new BranchingStreamWrapper[BRANCHES];
        boolean still = true;
        int counter = 0; // counts number of reads
        while (still) {
            still = false; // start assuming there is no work done
            for (int b = 0; b < BRANCHES; b++) {
                if (branches[b] == null || branches[b].isClosed()) {
                    branches[b] = null;
                    for (int n = b - 1; n >= 0 && branches[b] == null; n--) {
                        if (branches[n] != null && !branches[n].isClosed()) {
                            branches[b] = branches[n].branch();
                            offsets[b] = offsets[n];
                        }
                    }
                    if (branches[b] == null) {
                        branches[b] = trunk.branch();
                        offsets[b] = 0;
                    }
                }
                if (offsets[b] < BUFSIZE) {
                    still = true; // there is work done, so loop again
                    int length = branches[b].read(BUFFERS[b], 0, nextReadLength());
                    assertThat(length).isPositive();
                    assertThat(Arrays.copyOfRange(BUFFERS[b], 0, length))
                            .isEqualTo(Arrays.copyOfRange(BYTES, offsets[b], offsets[b] + length));
                    offsets[b] += length;
                    if (++counter >= 51 && branches[b].parent() != null) {
                        branches[b].close(); // every 51st read closes stream and its children
                        counter = 0;
                    }
                } else {
                    assertThat(branches[b].read() < 0);
                }
            }
        }
        for (int b = 0; b < BRANCHES; b++) {
            if (branches[b] != null) {
                assertThat(branches[b].read() == -1);
            }
        }
        // purposely leave branches open to let trunk close them recursively
    }
}
