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
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2014 ForgeRock AS.
 */

package org.forgerock.resource.core;

import java.util.Iterator;

public interface Context extends Iterable<Context> {

    String getContextName();

    <T extends Context> T asContext(Class<T> clazz);

    Context getContext(String contextName);

    boolean containsContext(Class<? extends Context> clazz);

    boolean containsContext(String contextName);

    String getId();

    Context getParent();

    boolean isRootContext();

    Iterator<Context> iterator();

    <T extends Context> Iterator<T> iterator(Class<T> clazz);
}
