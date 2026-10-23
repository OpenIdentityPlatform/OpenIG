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

package org.forgerock.openig.text;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class SeparatedValuesFileTest {

    @Test
    public void shouldReadRecordFromFile() throws Exception {
        Path csv = Files.createTempFile("users", ".csv");
        try {
            Files.write(csv, "username,password\njoe,secret\nbob,hunter2\n".getBytes(UTF_8));

            SeparatedValuesFile file = new SeparatedValuesFile(csv.toFile());

            assertThat(file.getRecord("username", "bob"))
                    .containsEntry("username", "bob")
                    .containsEntry("password", "hunter2");
            assertThat(file.getRecord("username", "nobody")).isNull();
            assertThat(file.getRecord("missing", "joe")).isNull();
        } finally {
            Files.delete(csv);
        }
    }
}
