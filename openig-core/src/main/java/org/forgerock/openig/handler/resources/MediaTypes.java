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

package org.forgerock.openig.handler.resources;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for obtaining the media type matching a given file extension.
 */
final class MediaTypes {
    private final static Logger logger = LoggerFactory.getLogger(MediaTypes.class);

    private final static Map<String, String> EXTENSIONS = new HashMap<>();

    private MediaTypes() { }

    static {
        // file format:
        // media-type extension(s)
        // image/jpeg jpg jpeg
        try (InputStream stream = MediaTypes.class.getResourceAsStream("media-types.properties")) {
            Properties p = new Properties();
            p.load(stream);
            for (String type : p.stringPropertyNames()) {
                String extensions = p.getProperty(type, "");

                for (String extension : extensions.split(" ")) {
                    EXTENSIONS.put(extension, type);
                }
            }
        } catch (IOException e) {
            logger.warn("Cannot read media-types.properties", e);
        }
    }

    /**
     * Find the media-type associated with a given {@code ext} (lower-cased).
     * @param extension file extension
     * @return the matching media-type or {@code null} if not matching type is found
     */
    static String getMediaType(String extension) {
        return EXTENSIONS.get(extension.toLowerCase(Locale.ROOT));
    }

    /**
     * Extract file extension (everything after the last {@literal .}) from the given {@code path}.
     * @param path resource file name
     * @return the extension, or {@code ""} if no extension can be computed.
     */
    static String extensionOf(String path) {
        int dotIndex = path.lastIndexOf('.');
        if (dotIndex >= 0) {
            return path.substring(dotIndex + 1);
        }
        return "";
    }
}
