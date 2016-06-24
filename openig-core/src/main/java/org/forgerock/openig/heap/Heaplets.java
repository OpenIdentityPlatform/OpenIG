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
 * Copyright 2010-2011 ApexIdentity Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */

package org.forgerock.openig.heap;

import static java.util.Collections.*;

import java.util.List;

import org.forgerock.http.util.Loader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads {@link Heaplet} classes based on the class of object they create. Three methods of
 * locating the heaplet class are attempted, in the following order:
 * <ol>
 * <li>The {@link Loader} class attempts to locate a {@code HeapletFactory}
 * interface implementation suitable for the class being created.</li>
 * <li>A nested {@code Heaplet} class is searched for. Example: creating
 * {@code com.example.Foo} would search for a heaplet class named
 * {@code com.example.Foo$Heaplet}.</li>
 * <li>A standalone class with the name {@code Heaplet} appended. Example: creating
 * {@code com.example.Foo} would search for a heaplet class named
 * {@code com.example.FooHeaplet}. </li>
 * </ol>
 */
public final class Heaplets {

    private static final Logger logger = LoggerFactory.getLogger(Heaplets.class);

    /** List of classpath-discovered {@link HeapletFactory} services. */
    private static final List<HeapletFactory> SERVICES = unmodifiableList(Loader.loadList(HeapletFactory.class));

    /** Static methods only. */
    private Heaplets() {
    }

    /**
     * Returns the heaplet that creates an instance of the specified class, or {@code null}
     * if no such heaplet could be found.
     *
     * @param c the class that the heaplet is responsible for creating.
     * @return the heaplet that creates the specified class, or {@code null} if not found.
     */
    public static Heaplet getHeaplet(Class<?> c) {

        Heaplet heaplet;

        // try service loader
        for (HeapletFactory factory : SERVICES) {
            heaplet = factory.newInstance(c);
            if (heaplet != null) {
                return heaplet;
            }
        }

        // try nested class
        heaplet = Loader.newInstance(c.getName() + "$Heaplet", Heaplet.class);

        if (heaplet == null) {
            // try standalone class
            heaplet = Loader.newInstance(c.getName() + "Heaplet", Heaplet.class);
        }

        // we're directly pointing to the Heaplet
        if (Heaplet.class.isAssignableFrom(c)) {
            try {
                heaplet = c.asSubclass(Heaplet.class).newInstance();
            } catch (Exception e) {
                logger.warn("An error occurred while trying to instantiate %s as a Heaplet", c.getName(), e);
                // Ignored
            }
        }
        return heaplet;
    }
}
