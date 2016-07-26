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

/**
 * A {@link ResourceSet} abstracts {@link Resource} lookup mechanism.
 *
 * <p>{@link ResourceSet} implementations have to make sure that they don't return resources outside of their scopes.
 * For instance, the {@link FileResourceSet} verifies that the found resource is really a child file of the
 * root directory (imagine that the path to lookup is {@literal ../../etc/passwd}).
 */
public interface ResourceSet {

    /**
     * Finds a {@link Resource} matching the given {@code path}. Returns {@code null} if no resource can be found
     * or if any error happened during lookup.
     * @param path resource path
     * @return a matching {@link Resource} or {@code null} if not found or failure.
     */
    Resource find(String path);
}
