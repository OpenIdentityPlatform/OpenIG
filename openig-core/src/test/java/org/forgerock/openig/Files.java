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
 * Copyright 2016 ForgeRock AS.
 */

package org.forgerock.openig;

import static java.io.File.createTempFile;
import static java.lang.String.format;
import static java.nio.file.Files.write;
import static org.forgerock.http.util.Json.writeJson;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.StandardOpenOption;

import org.forgerock.json.JsonValue;

/**
 * {@link File} related utility methods.
 */
@SuppressWarnings("javadoc")
public final class Files {
    private Files() { }

    public static File getRelative(final Class<?> base, final String name) {
        final URL url = base.getResource(base.getSimpleName() + ".class");
        File f;
        try {
            f = new File(url.toURI());
        } catch (URISyntaxException e) {
            f = new File(url.getPath());
        }
        return new File(f.getParentFile(), name);
    }

    public static File getRelativeDirectory(final Class<?> base, final String name) throws IOException {
        final File file = getRelative(base, name);
        if (!file.isDirectory()) {
            throw new IOException(
                    format("Path '%s', relative to %s is not a directory (or does not exist)", name, base)
            );
        }
        return file;
    }

    public static File getRelativeFile(final Class<?> base, final String name) throws IOException {
        final File file = getRelative(base, name);
        if (!file.isFile()) {
            throw new IOException(
                    format("Path '%s', relative to %s is not a file (may be a directory or does not exist)",
                           name,
                           base)
            );
        }
        return file;
    }

    public static File temporaryJsonFile(String prefix, JsonValue json) throws IOException {
        File file = createTempFile(prefix, ".json");
        file.deleteOnExit();
        write(file.toPath(), writeJson(json), StandardOpenOption.CREATE);

        return file;
    }
}
