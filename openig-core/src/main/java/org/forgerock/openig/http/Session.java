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

package org.forgerock.openig.http;

import java.util.Map;

/**
 * An interface for managing attributes across multiple requests from the same user agent.
 * Implementations should expose underlying container session attributes through this
 * interface if applicable.
 * <p/>
 * New keys added to a session object should be named in a manner that avoids possible
 * collision with keys added by other objects in the heap.
 */
public interface Session extends Map<String, Object> {
}
