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
 * information: "Portions Copyrighted [year] [name of copyright owner]".
 *
 * Copyright © 2010–2011 ApexIdentity Inc. All rights reserved.
 * Portions Copyrighted 2011-2014 ForgeRock AS.
 */

package org.forgerock.openig.config;

import java.io.File;
import java.net.URI;
import java.util.regex.Pattern;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.fluent.JsonValueException;
import org.forgerock.openig.json.JSONRepresentation;
import org.forgerock.openig.resource.FileResource;
import org.forgerock.openig.resource.Representation;
import org.forgerock.openig.resource.Resource;
import org.forgerock.openig.resource.ResourceException;
import org.forgerock.openig.resource.Resources;

/**
 * A resource for accessing application configuration data.
 * <p/>
 * There are two modes the configuration resource resolve: simple and bootstrap.
 * Simple mode is meant for single local deployments of a web application in a
 * user account; bootstrap mode supports remotely configured or multiple
 * deployments of an application for a given user account.
 * <p/>
 * In both cases, all file resources are located a directory that is either
 * <tt><strong>$AppData/</strong><em>product</em><strong>/</strong></tt> if the
 * <strong>{@code $AppData}</strong> environment variable is defined (typical in
 * Windows installations), or otherwise
 * <tt><em>user-home</em><strong>/.</strong><em>product</em><strong>/</strong></tt>
 * (typical in Unix installations).
 * <p/>
 * This class first tries to locate the configuration file using simple mode by
 * looking for a file named <strong>{@code config.json}</strong> in the
 * configuration directory specified above. If the file does not exist, then
 * this class reverts to bootstrap mode.
 * <p/>
 * In bootstrap mode, the name of a bootstrap configuration resource is
 * generated based on the instance name supplied (or derived from the servlet
 * context) and takes the form <tt><em>instance</em><strong>.json</strong></tt>.
 * This file is expected to contain a single JSON object with a single value
 * with the name <strong>{@code configURI}</strong>. The value is the URI of the
 * configuration resource.
 *
 * @see Environment
 */
public class ConfigResource implements Resource {

    /**
     * Characters to filter from filenames: (SP) ? < > | * [ ] = + " \ / , . : ;
     */
    private static final Pattern DIRTY = Pattern.compile("[ \\?<>|\\*\\[\\]=+\\\"\\\\/,\\.:;]");

    /** The underlying resource that this object represents. */
    private final Resource resource;

    // Called from Environment.
    ConfigResource(final Environment environment, final String instance) throws ResourceException {
        final File config = getFileInDirectory(environment.getConfigDir(), "config");
        if (config.exists()) {
            // simplistic config.json file
            this.resource = new FileResource(config);
        } else {
            // bootstrap location of instance-based configuration file
            final File boot =
                    getFileInDirectory(environment.getConfigDir(), instance != null ? instance
                            : "bootstrap");
            if (!boot.exists()) {
                throw new ResourceException("could not find local configuration file at "
                        + config.getPath() + " or bootstrap file at " + boot.getPath());
            }
            final FileResource bootResource = new FileResource(boot);
            final JSONRepresentation representation = new JSONRepresentation();
            bootResource.read(representation);
            try {
                this.resource =
                        Resources.newInstance(new JsonValue(representation.object).get("configURI")
                                .required().asURI());
            } catch (final JsonValueException jve) {
                throw new ResourceException(jve);
            }
        }
    }

    @Override
    public void create(final Representation representation) throws ResourceException {
        resource.create(representation);
    }

    @Override
    public void delete() throws ResourceException {
        resource.delete();
    }

    @Override
    public URI getURI() throws ResourceException {
        return resource.getURI();
    }

    @Override
    public void read(final Representation representation) throws ResourceException {
        resource.read(representation);
    }

    @Override
    public void update(final Representation representation) throws ResourceException {
        resource.update(representation);
    }

    private File getFileInDirectory(final File directory, final String instance) {
        return getFileInDirectory(directory, instance, JSONRepresentation.EXTENSION);
    }

    private File getFileInDirectory(final File directory, final String instance,
                                    final String extension) {
        final StringBuilder sb = new StringBuilder();
        sb.append(DIRTY.matcher(instance).replaceAll("_"));
        sb.append('.');
        sb.append(extension);
        return new File(directory, sb.toString());
    }
}
