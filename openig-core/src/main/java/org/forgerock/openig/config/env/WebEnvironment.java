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

import javax.servlet.ServletConfig;

import org.forgerock.openig.config.Environment;

/**
 * Represents an {@link Environment} built from a webapp.
 * It tries to create an environment from different sources (init-params, process-scoped values or default location).
 * It goes from the most specific one (servlet's init-params) to the default one (default platform specific location).
 *
 * @since 2.2
 */
public class WebEnvironment extends EnvironmentDelegate {

    /**
     * Servlet's {@literal init-param} name.
     * <pre>
     * &lt;servlet>
     *   &lt;servlet-name>GatewayServlet&lt;/servlet-name>
     *   &lt;servlet-class>org.forgerock.openig.servlet.GatewayServlet&lt;/servlet-class>
     *   &lt;init-param>
     *     &lt;param-name>openig-base&lt;/param-name>
     *     &lt;param-value>/my/openig/path&lt;/param-value>
     *   &lt;/init-param>
     * &lt;/servlet>
     * </pre>
     */
    public static final String BASE_INIT_PARAM = "openig-base";

    /**
     * System property name that can be specified through command line.
     * <code>
     *     java -Dopenig.base=/my/openig/path ....
     * </code>
     */
    public static final String BASE_SYSTEM_PROPERTY = "openig.base";

    /**
     * Environment variable name.
     *
     * Under UNIX:
     * <code>
     *     export OPENIG_BASE=/my/openig/path
     * </code>
     *
     * Under Windows:
     * <code>
     *     set OPENIG_BASE=c:\my\openig\path
     * </code>
     */
    public static final String BASE_ENV_VARIABLE = "OPENIG_BASE";

    /**
     * Delegatee.
     */
    private final Environment delegate;

    /**
     * Builds a new web environment.
     * @param config webapp's configuration
     */
    public WebEnvironment(final ServletConfig config) {
        String base = config.getInitParameter(BASE_INIT_PARAM);
        if (base != null) {
            delegate = new DefaultEnvironment(new File(base));
            return;
        }
        base = System.getProperty(BASE_SYSTEM_PROPERTY);
        if (base != null) {
            delegate = new DefaultEnvironment(new File(base));
            return;
        }
        base = System.getenv(BASE_ENV_VARIABLE);
        if (base != null) {
            delegate = new DefaultEnvironment(new File(base));
            return;
        }

        delegate = new PlatformEnvironment();

    }

    @Override
    protected Environment delegate() {
        return delegate;
    }

}
