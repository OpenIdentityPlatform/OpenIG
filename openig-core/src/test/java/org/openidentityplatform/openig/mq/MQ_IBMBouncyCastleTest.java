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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */

package org.openidentityplatform.openig.mq;

import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class MQ_IBMBouncyCastleTest {

    /**
     * com.ibm.mq.allclient runs on the bcprov-jdk15to18 pinned in the root pom, next to opendj-core's
     * bc-fips. Both put classes in org.bouncycastle.crypto, so they must carry the same signer.
     * The test classpath lists the FIPS jars first, the order in which a mismatch fails; it only
     * detects the clash while that order holds.
     */
    @Test
    public void mqAmsCipherClassesLinkNextToBcFips() throws Exception {
        // PaddedBufferedBlockCipher is only in bcprov-jdk15to18; its InvalidCipherTextException is also in bc-fips
        Class.forName("org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher")
                .getMethod("doFinal", byte[].class, int.class);
    }
}
