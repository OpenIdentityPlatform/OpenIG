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

import org.forgerock.openig.config.Environment;

/**
 * Delegates to a unix or windows type environment depending on the OS.
 *
 * @since 2.2
 */
public class PlatformEnvironment extends EnvironmentDelegate {

    /**
     * Delegatee.
     */
    private final Environment delegate;

    /**
     * Builds a new platform specific {@link Environment} depending on the {@literal os.name} system property.
     */
    public PlatformEnvironment() {
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            delegate = new WindowsEnvironment();
        } else {
            delegate = new UnixEnvironment();
        }

    }

    @Override
    protected Environment delegate() {
        return delegate;
    }
}
