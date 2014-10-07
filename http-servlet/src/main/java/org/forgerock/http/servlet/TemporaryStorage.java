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
 * Copyright 2014 ForgeRock AS.
 */

package org.forgerock.http.servlet;

import org.forgerock.http.io.Buffer;
import org.forgerock.http.io.IO;
import org.forgerock.util.Factory;

import java.io.File;

final class TemporaryStorage implements Factory<Buffer> {

    @Override
    public Buffer newInstance() { //TODO are the defaults acceptable? Will they ever need to be customizable?
        return IO.newTemporaryBuffer(IO.DEFAULT_TMP_INIT_LENGTH, IO.DEFAULT_TMP_MEMORY_LIMIT, IO.DEFAULT_TMP_FILE_LIMIT,
                new File(".")); //TODO where to get the directory from!
    }
}
