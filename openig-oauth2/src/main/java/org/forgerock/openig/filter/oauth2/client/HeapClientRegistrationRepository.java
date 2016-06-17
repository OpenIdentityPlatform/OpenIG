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
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.openig.filter.oauth2.client;

import static java.lang.String.format;

import java.util.List;

import org.forgerock.openig.heap.Heap;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.log.Logger;

/**
 * This class extends the {@link ClientRegistrationRepository} to ensure
 * compatibility between OPENIG 4.0 and 4.5. The {@link ClientRegistration}
 * defined in the heap can be retrieved and used in the
 * {@link OAuth2ClientFilter} without referencing them in the "registrations"
 * attributes.
 *
 * @see <a href="https://bugster.forgerock.org/jira/browse/OPENIG-953">OPENIG-953</a>
 */
class HeapClientRegistrationRepository extends ClientRegistrationRepository {

    private final Heap heap;
    private final Logger logger;

    HeapClientRegistrationRepository(final List<ClientRegistration> registrations,
                                     final Heap heap,
                                     final Logger logger) {
        super(registrations);
        this.heap = heap;
        this.logger = logger;
    }

    @Override
    ClientRegistration findByName(String name) {
        ClientRegistration reg = super.findByName(name);
        if (reg != null) {
            return reg;
        }
        try {
            reg = heap.get(name, ClientRegistration.class);
            logger.warning(format("The ClientRegistration '%s' needs to be declared in the OAuth2ClientFilter"
                                  + " 'registrations' attribute. Lookups in the heap will be deprecated in 5.0", name));
            if (reg != null) {
                add(reg);
            }
            return reg;
        } catch (HeapException ex) {
            logger.warning("Unable to extract the client registration from the heap");
            logger.warning(ex);
            return null;
        }
    }
}
