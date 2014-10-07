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

/**
 * This package contains the OAuth2 Token validation filter that acts as an OAuth 2 Resource Server.
 * <ul>
 *     <li>It ensure that there is an existing bearer access token in the request's headers.</li>
 *     <li>It resolves it against a given Authorization Server (that must provide a {@literal token-info}
 *     endpoint).
 *     <ul>
 *         <li>Produced {@link org.forgerock.openig.filter.oauth2.AccessToken} are cached for future re-use.</li>
 *         <li>Produced {@link org.forgerock.openig.filter.oauth2.AccessToken} are stored in the
 *         {@link org.forgerock.openig.http.Exchange} for downstream handlers/filters.</li>
 *     </ul>
 *     </li>
 *     <li>It performs token validation: checking expiration time and required scopes compliance.</li>
 * </ul>
 */
package org.forgerock.openig.filter.oauth2;
