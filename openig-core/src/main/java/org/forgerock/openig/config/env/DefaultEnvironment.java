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

package org.forgerock.openig.config.env;

import java.io.File;

import org.forgerock.openig.config.Environment;

/**
 * Reify the normal environment structure with pre-configured shortcuts.
 * <pre>
 *     conf/**.json
 *     scripts/groovy/**.groovy
 *     tmp/
 * </pre>
 *
 * @since 2.2
 */
public class DefaultEnvironment implements Environment {

    private final File base;

    /**
     * Builds a new file based {@link Environment} using the given file as the base directory.
     * @param base OpenIG base directory
     */
    public DefaultEnvironment(final File base) {
        this.base = base;
    }

    @Override
    public File getBaseDirectory() {
        return base;
    }

    @Override
    public File getTempDirectory() {
        return new File(base, "tmp");
    }

    @Override
    public File getScriptDirectory(final String type) {
        return new File(new File(base, "scripts"), type);
    }

    @Override
    public File getConfigDirectory() {
        return new File(base, "config");
    }
}
