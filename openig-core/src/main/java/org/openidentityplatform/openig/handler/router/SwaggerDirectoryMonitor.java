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
 * Copyright 2026 3A Systems LLC.
 */

package org.openidentityplatform.openig.handler.router;

import org.forgerock.openig.handler.router.AbstractDirectoryMonitor;

import java.io.File;
import java.io.FileFilter;
import java.util.HashMap;

public class SwaggerDirectoryMonitor extends AbstractDirectoryMonitor {

    public SwaggerDirectoryMonitor(File routes) {
        super(routes, new HashMap<>());
    }

    @Override
    protected FileFilter getFileFilter() {
        return path -> path.isFile() &&
                (path.getName().endsWith(".json")
                                || path.getName().endsWith(".yaml")
                                || path.getName().endsWith("*.yml"));
    }
}
