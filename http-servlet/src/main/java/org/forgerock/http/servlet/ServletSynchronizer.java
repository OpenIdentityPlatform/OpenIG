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
 * Copyright 2012-2014 ForgeRock AS.
 */

package org.forgerock.http.servlet;

/**
 * An interface for implementing different synchronization strategies depending
 * on the available Servlet container version and/or capabilities. These include:
 * <ul>
 * <li>2.5 (JavaEE 5) - synchronous processing and synchronous IO
 * <li>3.0 (JavaEE 6) - asynchronous (non-blocking) processing and synchronous
 * IO
 * <li>3.1 (JavaEE 7) - asynchronous (non-blocking) processing and asynchronous
 * IO (NIO)
 * </ul>
 *
 * @see ServletVersionAdapter#createServletSynchronizer(javax.servlet.http.HttpServletRequest,
 *      javax.servlet.http.HttpServletResponse)
 * @since 1.0.0
 */
interface ServletSynchronizer {

    /**
     * If this synchronizer is non-blocking then this method registers an
     * {@link javax.servlet.AsyncListener call-back} which will be invoked once
     * the request has completed, failed, or timed out.
     * <p>
     * This method should be used to register call-backs for releasing resources
     * after a request has been processed.
     * <p>
     * Calls to this method when the synchronizer is blocking have no effect.
     *
     * @param runnable
     *            The call-back to be invoked once the request has completed,
     *            failed, or timed out.
     */
    void setAsyncListener(Runnable runnable);

    /**
     * Waits for this synchronizer to be signalled but only if this synchronizer
     * is a blocking implementation.
     *
     * @throws InterruptedException
     *             If an unexpected error occurred while waiting to be
     *             signalled.
     */
    void awaitIfNeeded() throws InterruptedException;

    /**
     * Releases any waiting threads blocked on {@link #awaitIfNeeded()}, as well
     * as completing the underlying {@link javax.servlet.AsyncContext
     * AsyncContext} if this synchronizer is non-blocking. This method should be
     * called after a writing out content and setting the response status.
     */
    void signalAndComplete();
}
