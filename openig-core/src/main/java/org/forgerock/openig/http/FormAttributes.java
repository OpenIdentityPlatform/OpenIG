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

// TODO: more permanent way to expose "exchange.request.form" parameters.

package org.forgerock.openig.http;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.forgerock.openig.io.BranchingInputStream;
import org.forgerock.openig.util.UnmodifiableCollection;

/**
 * Exposes query parameters and posted form entity as values.
 */
public class FormAttributes extends AbstractMap<String, List<String>> implements
        Map<String, List<String>>, UnmodifiableCollection {

    /** The request to read form attributes from. */
    private final Request request;

    /**
     * Constructs a new form attributes object that reads attributes from the
     * specified request.
     *
     * @param request the request to read form attributes from.
     */
    public FormAttributes(final Request request) {
        this.request = request;
    }

    @Override
    public boolean containsKey(final Object key) {
        return form().containsKey(key);
    }

    @Override
    public boolean containsValue(final Object value) {
        return form().containsValue(value);
    }

    @Override
    public Set<Entry<String, List<String>>> entrySet() {
        return form().entrySet();
    }

    @Override
    public List<String> get(final Object name) {
        return form().get(name);
    }

    @Override
    public boolean isEmpty() {
        return form().isEmpty();
    }

    @Override
    public Set<String> keySet() {
        return form().keySet();
    }

    @Override
    public int size() {
        return form().size();
    }

    @Override
    public String toString() {
        return form().toString();
    }

    @Override
    public Collection<List<String>> values() {
        return form().values();
    }

    private Form form() {
        BranchingInputStream entity = null;
        if (request.entity != null) {
            entity = request.entity;
            try {
                request.entity = entity.branch();
            } catch (final IOException ioe) {
                throw new IllegalStateException(ioe);
            }
        }
        final Form form = new Form();
        try {
            form.fromRequestQuery(request);
            form.fromRequestEntity(request);
        } catch (final IOException ioe) {
            // Ignore: return empty form.
        } finally {
            if (entity != null) {
                try {
                    entity.closeBranches();
                } catch (final IOException ioe) {
                    throw new IllegalStateException(ioe);
                }
            }
            request.entity = entity;
        }
        return form;
    }
}
