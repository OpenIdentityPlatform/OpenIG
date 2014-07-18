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

package org.forgerock.openig.config;

import java.io.File;

/**
 * Encapsulate logic to access configuration files and other directories of the OpenIG base directory.
 * A typical structure may looks like the following:
 * <pre>
 *     config/config.json
 *     scripts/groovy/**.groovy
 *     tmp/
 * </pre>
 *
 * This interface provides an abstraction over the directory layout to protect against changes of naming, ...
 */
public interface Environment {

    /**
     * Key to retrieve an {@link Environment} instance from the {@link org.forgerock.openig.heap.Heap}.
     */
    public static final String ENVIRONMENT_HEAP_KEY = "Environment";

    /**
     * Returns the base directory of the OpenIG file system.
     * It can be used to access resources that are not part of the standard layout.
     * @return the base directory of the OpenIG file system.
     */
    File getBaseDirectory();

    /**
     * Returns the temporary directory of OpenIG (where we have read+write permissions).
     * It usually points to the {@literal tmp/} directory.
     * @return the working directory.
     */
    File getTempDirectory();

    /**
     * Returns the directory that contains the files of the given type. It
     * usually points to the {@literal scripts/<type>/} directory.
     *
     * @param type
     *            script's type (could be {@literal groovy} or {@literal js})
     * @return the scripting directory.
     */
    File getScriptDirectory(String type);

    /**
     * Returns the directory that contains the configuration files.
     * It usually points to the {@literal config/} directory.
     * @return the configuration directory.
     */
    File getConfigDirectory();

}
