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
 * @see ServletApiVersionAdapter#createServletSynchronizer(javax.servlet.http.HttpServletRequest,
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
    void addAsyncListener(Runnable runnable);

    /**
     * Waits for this synchronizer to be signalled but only if this synchronizer
     * is a blocking implementation. More specifically, this method will only
     * block if {@link #isAsync()} returns {@code false}, otherwise it will
     * return immediately without waiting to be signalled.
     *
     * @throws Exception
     *             If an unexpected error occurred while waiting to be
     *             signalled.
     */
    void awaitIfNeeded() throws Exception;

    /**
     * Returns {@code true} if this synchronizer is non-blocking. In other
     * words, if this method returns {@code false} then {@link #awaitIfNeeded()}
     * may block and calls to {@link #addAsyncListener(Runnable)} will be
     * ignored.
     * <p>
     * This method should be used in order to determine whether filters should
     * invoke
     * {@link javax.servlet.Filter#doFilter(javax.servlet.ServletRequest,
     * javax.servlet.ServletResponse, javax.servlet.FilterChain)
     * doFilter()} before returning.
     *
     * @return {@code true} if this synchronizer is non-blocking.
     */
    boolean isAsync();

    /**
     * Releases any waiting threads blocked on {@link #awaitIfNeeded()}. This
     * method WILL NOT complete the underlying
     * {@link javax.servlet.AsyncContext} even if one is present. It is intended
     * for use within Filters in order to signal that the thread processing the
     * request should forward the request to the remainder of the filter chain.
     */
    void signal();

    /**
     * Releases any waiting threads blocked on {@link #awaitIfNeeded()}, as well
     * as completing the underlying {@link javax.servlet.AsyncContext
     * AsyncContext} if this synchronizer is non-blocking. This method should be
     * called after a writing out content and setting the response status. Use
     * {@link #signalAndComplete(Throwable)} for returning errors.
     */
    void signalAndComplete();

    /**
     * Releases any waiting threads blocked on {@link #awaitIfNeeded()}, as well
     * as completing the underlying {@link javax.servlet.AsyncContext
     * AsyncContext} if this synchronizer is non-blocking. This method should be
     * called when an error is encountered which prevents the request from being
     * processed any further.
     *
     * @param t //FIXME will the error be adapted to a ResourceException?
     *            The error that occurred. The error will be adapted to a
     *            {@link org.forgerock.json.resource.ResourceException
     *            ResourceException} if it not already one before being
     *            serialized back to the client.
     */
    void signalAndComplete(Throwable t);
}
