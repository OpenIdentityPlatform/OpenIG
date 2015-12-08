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
 * Copyright 2014-2015 ForgeRock AS.
 */

package org.forgerock.openig.audit;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;

/**
 * Static list of standard tags used in audit decorators.
 * String values not included in this set are considered 'user-defined' tags.
 * <p>
 * Notice that, when audit decorator developers are adding new tags, they have to keep this set of values in sync.
 */
@Deprecated
public enum Tag {

    /**
     * The event happens before the delegate {@link Filter}/{@link Handler} is called.
     */
    request,

    /**
     * The event happens after the delegate {@link Filter}/{@link Handler} was called.
     */
    response,

    /**
     * The event happens when the request has been completely handled <b>successfully</b>
     * by the processing unit (always complements a {@link #response} tag).
     */
    completed,

    /**
     * The event happens when the request has been handled with <b>errors</b>
     * by the processing unit (always complements a {@link #response} tag). Notice that this does not indicate that
     * the source heap object is the origin of the failure (it may or may not have thrown the exception itself).
     */
    exception
}
