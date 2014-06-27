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

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Ensure that the delegate {@link DirectoryScanner} will be executed only once.
 *
 * @since 2.2
 */
class OnlyOnceDirectoryScanner implements DirectoryScanner {

    /**
     * Delegate.
     */
    private final DirectoryScanner delegate;

    /**
     * Contains {@literal false} if the delegate has never been executed before.
     * Its value is set to {@literal true} at first execution.
     */
    private final AtomicBoolean executed = new AtomicBoolean(false);

    /**
     * Builds a OnlyOnceDirectoryScanner wrapping the given scanner.
     * @param delegate delegate scanner
     */
    public OnlyOnceDirectoryScanner(final DirectoryScanner delegate) {
        this.delegate = delegate;
    }

    @Override
    public void scan(final FileChangeListener listener) {
        // If it has not already been executed
        if (executed.compareAndSet(false, true)) {
            delegate.scan(listener);
        }
    }
}
