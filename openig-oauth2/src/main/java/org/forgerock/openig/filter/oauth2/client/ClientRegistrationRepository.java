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

import static org.forgerock.util.Reject.checkNotNull;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * This class handles the {@link ClientRegistration}s defined in the
 * {@link OAuth2ClientFilter} using {@link ReentrantReadWriteLock} to improve
 * concurrency.
 */
class ClientRegistrationRepository {

    private List<ClientRegistration> registrations;
    private Map<String, ClientRegistration> registrationsByName;
    private Map<Issuer, ClientRegistration> registrationsByIssuer;
    private final ReadWriteLock rwl = new ReentrantReadWriteLock();
    private final Lock r = rwl.readLock();
    private final Lock w = rwl.writeLock();

    ClientRegistrationRepository() {
        this(new LinkedList<ClientRegistration>());
    }

    ClientRegistrationRepository(List<ClientRegistration> registrations) {
        this.registrations = checkNotNull(registrations);
        this.registrationsByName = new HashMap<>(registrations.size());
        this.registrationsByIssuer = new HashMap<>(registrations.size());
        for (final ClientRegistration cr : registrations) {
            registrationsByName.put(cr.getName(), cr);
            registrationsByIssuer.put(cr.getIssuer(), cr);
        }
    }

    void add(ClientRegistration clientRegistration) {
        w.lock();
        try {
            registrations.add(clientRegistration);
            registrationsByName.put(clientRegistration.getName(), clientRegistration);
            registrationsByIssuer.put(clientRegistration.getIssuer(), clientRegistration);
        } finally {
            w.unlock();
        }
    }

    ClientRegistration findByName(String name) {
        if (name == null) {
            return null;
        }
        r.lock();
        try {
            return registrationsByName.get(name);
        } finally {
            r.unlock();
        }
    }

    ClientRegistration findByIssuer(Issuer issuer) {
        if (issuer == null) {
            return null;
        }
        r.lock();
        try {
            return registrationsByIssuer.get(issuer);
        } finally {
            r.unlock();
        }
    }

    ClientRegistration findDefault() {
        if (registrations.isEmpty()) {
            return null;
        }
        r.lock();
        try {
            return registrations.get(0);
        } finally {
            r.unlock();
        }
    }

    boolean needsNascarPage() {
        r.lock();
        try {
            return registrations.size() != 1;
        } finally {
            r.unlock();
        }
    }
}
