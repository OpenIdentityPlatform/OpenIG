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

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class to retrieve version information about this project.
 */
public final class VersionUtil {

    private static final Logger logger = LoggerFactory.getLogger(VersionUtil.class);

    private static final Properties PROPERTIES;
    static {
        PROPERTIES = new Properties();
        try (final InputStream is = VersionUtil.class.getResourceAsStream("/org/forgerock/openig/build.properties")) {
            PROPERTIES.load(is);
        } catch (IOException e) {
            logger.error("Unable to load build properties file", e);
            setDefaultProperties();
        }
    }

    private VersionUtil() { }

    private static void setDefaultProperties() {
        PROPERTIES.setProperty("branch", "n/a");
        PROPERTIES.setProperty("revision", "n/a");
        PROPERTIES.setProperty("timestamp", "0");
        PROPERTIES.setProperty("version", "latest");
    }

    /**
     * Returns the OpenIG version.
     *
     * @return the OpenIG version.
     */
    public static String getVersion() {
        return PROPERTIES.getProperty("version");
    }

    /**
     * Returns the branch name.
     *
     * @return the branch name.
     */
    public static String getBranch() {
        return PROPERTIES.getProperty("branch");
    }

    /**
     * Returns the revision.
     *
     * @return the revision.
     */
    public static String getRevision() {
        return PROPERTIES.getProperty("revision");
    }

    /**
     * Returns the timestamp.
     *
     * @return the timestamp.
     */
    public static long getTimeStamp() {
        return Long.valueOf(PROPERTIES.getProperty("timestamp"));
    }
}
