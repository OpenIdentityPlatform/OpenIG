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

package org.forgerock.openig.web;

import static java.util.Collections.singletonList;
import static org.forgerock.http.routing.RouteMatchers.requestUriMatcher;
import static org.forgerock.http.routing.RoutingMode.STARTS_WITH;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import org.forgerock.http.io.IO;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.config.Environment;
import org.forgerock.openig.handler.resources.FileResourceSet;
import org.forgerock.openig.handler.resources.ResourceHandler;
import org.forgerock.openig.handler.resources.ResourceSet;
import org.forgerock.openig.http.AdminHttpApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extension of the admin module that is responsible to serve the UI.
 */
class ConsoleAdminHttpApplication extends AdminHttpApplication {

    private static final Logger logger = LoggerFactory.getLogger(ConsoleAdminHttpApplication.class);

    /**
     * Construct a {@link ConsoleAdminHttpApplication}.
     *
     * @param prefix the prefix to use in the URL to access the admin endpoints
     * @param config the admin configuration
     * @param environment the OpenIG environment
     * @throws IOException when unpack fails
     */
    ConsoleAdminHttpApplication(final String prefix, final JsonValue config, final Environment environment)
            throws IOException {
        super(prefix, config, environment);

        // Grab the openig-ui.jar as a classloader resource
        URL url = getClass().getResource("/openig-ui.jar");
        if (url == null) {
            throw new IOException("Can't find openig-ui.jar as a classloader resource");
        }

        // Unpack it in the OpenIG temp directory (create sub-directory)
        File unpack = new File(environment.getTempDirectory(), "openig-ui");
        try (JarInputStream jar = new JarInputStream(new BufferedInputStream(url.openStream()))) {
            JarEntry entry = jar.getNextJarEntry();
            while (entry != null) {
                if (!entry.isDirectory()) {
                    unpackFileEntry(jar, entry, new File(unpack, entry.getName()));
                }
                // Close and move to the next entry
                jar.closeEntry();
                entry = jar.getNextJarEntry();
            }
        }

        // Create a FileResourceSet around that directory
        ResourceSet resources = new FileResourceSet(unpack);

        // Create a ResourceHandler
        ResourceHandler handler = new ResourceHandler(singletonList(resources), singletonList("index.html"));

        // Register it in the router under the /openig/console path
        getOpenIGRouter().addRoute(requestUriMatcher(STARTS_WITH, "console"), handler);
    }

    private static void unpackFileEntry(final JarInputStream jar, final JarEntry entry, final File destination)
            throws IOException {
        // Prepare parent directories if they do not exists yet
        File parentFile = destination.getParentFile();
        if (!parentFile.isDirectory() && !parentFile.mkdirs()) {
            throw new IOException("Can't create parent directories for " + parentFile);
        }

        // Only unpack when the resource doesn't exist or has been modified since last unpack
        if (destination.isFile()) {
            // Resource have already been unpacked before
            if (entry.getTime() > destination.lastModified()) {
                // Resource in the zip is newer than the unpacked resource
                logger.debug("Updating UI resource {}", entry.getName());
                unpack(jar, destination);
            } else {
                // Unpacked resource has been either not modified (same timestamp) or locally modified
                logger.debug("UI resource {} already unpacked, keeping that version", entry.getName());
            }
        } else {
            // There is no locally matching resource, unpack safely
            unpack(jar, destination);
        }
    }

    private static void unpack(JarInputStream stream, File destination) throws IOException {
        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(destination))) {
            IO.stream(stream, os);
        }
    }
}
