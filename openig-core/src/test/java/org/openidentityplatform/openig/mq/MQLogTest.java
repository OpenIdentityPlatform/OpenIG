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

import static org.assertj.core.api.Assertions.assertThat;

import org.testng.annotations.Test;

import com.ibm.msg.client.wmq.WMQConstants;

@SuppressWarnings("javadoc")
public class MQLogTest {

    @Test
    public void shouldStripLineBreaksFromConsumedMessage() {
        assertThat(MQLog.forLog("x\r\n12:00:00:000 | INFO  | forged\rline\nline"))
                .isEqualTo("x  12:00:00:000 | INFO  | forged line line");
    }

    @Test
    public void shouldLogNullMessageAsNull() {
        assertThat(MQLog.forLog(null)).isEqualTo("null");
    }

    @Test
    public void shouldMaskPasswordInSettingsDump() {
        assertThat(MQ_IBM.settingForLog(WMQConstants.PASSWORD, "passw0rd")).isEqualTo("***");
    }

    @Test
    public void shouldNotMaskOtherSettingsInSettingsDump() {
        assertThat(MQ_IBM.settingForLog(WMQConstants.USERID, "app")).isEqualTo("app");
    }
}
