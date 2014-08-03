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

package org.forgerock.openig.io;

import java.io.IOException;
import java.io.InputStream;

/**
 * An input stream that can branch into separate input streams to perform
 * divergent reads.
 */
public abstract class BranchingInputStream extends InputStream {

    /**
     * Creates a new branch at this stream's current position.
     *
     * @return a new branching stream, in the same position as this branch.
     * @throws IOException
     *             if an I/O exception occurs.
     */
    public abstract BranchingInputStream branch() throws IOException;

    /**
     * Closes this branching stream and all of the branches created from it.
     *
     * @throws IOException
     *             if an I/O exception occurs.
     */
    public abstract void close() throws IOException;

    /**
     * Returns the parent branching input stream from which this branch was
     * created, or {@code null} if this is the trunk.
     *
     * @return the parent branching input stream from which this branch was
     *         created, or {@code null} if this is the trunk.
     */
    public abstract BranchingInputStream parent();
}
