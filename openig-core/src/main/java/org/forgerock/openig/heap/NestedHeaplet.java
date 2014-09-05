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
 * Copyright 2010–2011 ApexIdentity Inc.
 * Portions Copyright 2011-2014 ForgeRock AS.
 */

package org.forgerock.openig.heap;

/**
 * A generic base class for a heaplet nested within the class it creates. Using a nested
 * heaplet has the advantage of avoiding loading classes unless the requested class is
 * actually being created in a heap.
 * <p>
 * <strong>DEPRECATION NOTICE</strong>: This class has been deprecated as for OpenIG 3.1:
 * Heaplets don't require to be {@code Indexed} anymore. Please extends directly {@link GenericHeaplet} instead.
 *
 * @see GenericHeaplet
 */
@Deprecated
public abstract class NestedHeaplet extends GenericHeaplet { }
