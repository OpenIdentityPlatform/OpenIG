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
package org.forgerock.openig.util;

import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.util.JsonValues.readJson;

import java.io.IOException;

import org.forgerock.json.JsonValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class to retrieve version information about this project.
 */
public final class VersionUtil {

    private static final Logger logger = LoggerFactory.getLogger(VersionUtil.class);

    private static final JsonValue VERSION_INFO = json(object(field("branch", "n/a"),
                                                              field("revision", "n/a"),
                                                              field("timestamp", 0),
                                                              field("version", "latest")));
    static {
        final String versionInfoPath = "/org/forgerock/openig/build.json";
        try {
            // mutate the info with real values
            VERSION_INFO.setObject(readJson(VersionUtil.class.getResource(versionInfoPath)).getObject());
        } catch (IOException e) {
            logger.error("Unable to load the version info file '{}'", versionInfoPath, e);
        }
    }

    private VersionUtil() { }


    /**
     * Returns the OpenIG version.
     *
     * @return the OpenIG version.
     */
    public static String getVersion() {
        return VERSION_INFO.get("version").asString();
    }

    /**
     * Returns the branch name.
     *
     * @return the branch name.
     */
    public static String getBranch() {
        return VERSION_INFO.get("branch").asString();
    }

    /**
     * Returns the revision.
     *
     * @return the revision.
     */
    public static String getRevision() {
        return VERSION_INFO.get("revision").asString();
    }

    /**
     * Returns the timestamp.
     *
     * @return the timestamp.
     */
    public static long getTimeStamp() {
        return VERSION_INFO.get("timestamp").asLong();
    }
}
