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

package org.forgerock.openig.handler.router;

/**
 * A scanner is responsible to detect added, removed and modified files in a directory.
 *
 * @since 2.2
 * @see FileChangeListener
 * @see DirectoryMonitor
 */
interface DirectoryScanner {

    /**
     * Triggers a directory scan.
     *
     * @param listener Callback to be invoked if there are changes to be notified.
     */
    void scan(FileChangeListener listener);
}
