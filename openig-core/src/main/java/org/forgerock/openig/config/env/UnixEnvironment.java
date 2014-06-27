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
 * Represents a UNIX default environment.
 *
 * @since 2.2
 */
public class UnixEnvironment extends EnvironmentDelegate {

    /**
     * Delegatee.
     */
    private final Environment delegate;

    /**
     * Builds a new UNIX environment that will be located in the user's directory.
     * <pre>
     *     $HOME/.openig/
     *       conf/**.json
     * </pre>
     */
    public UnixEnvironment() {
        String home = System.getProperty("user.home");
        delegate = new DefaultEnvironment(new File(home, ".openig"));
    }

    @Override
    protected Environment delegate() {
        return delegate;
    }
}
