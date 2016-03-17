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

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.openig.filter.oauth2.client.OAuth2TestUtils.buildClientRegistration;
import static org.forgerock.openig.filter.oauth2.client.OAuth2TestUtils.buildIssuerWithoutWellKnownEndpoint;
import static org.mockito.MockitoAnnotations.initMocks;

import java.util.LinkedList;
import java.util.List;

import org.forgerock.http.Handler;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ClientRegistrationRepositoryTest {

    private static final String DEFAULT_ISSUER_NAME = "OpenAM";
    private static final String DEFAULT_CLIENT_REGISTRATION_NAME = "ForgeShop";

    private ClientRegistration clientRegistration;
    private Issuer issuer;
    private List<ClientRegistration> registrations;

    @Mock
    private Handler registrationHandler;

    @BeforeMethod
    public void setUp() throws Exception {
        initMocks(this);
        issuer = buildIssuerWithoutWellKnownEndpoint(DEFAULT_ISSUER_NAME);
        clientRegistration = buildClientRegistration(DEFAULT_CLIENT_REGISTRATION_NAME,
                                                     registrationHandler,
                                                     DEFAULT_ISSUER_NAME);
        registrations = new LinkedList<>();
        registrations.add(clientRegistration);
    }

    @SuppressWarnings("unused")
    @Test(expectedExceptions = NullPointerException.class)
    public void shouldNotAllowNull() {
        new ClientRegistrationRepository(null);
    }

    @Test
    public void shouldGetDefault() {
        final ClientRegistrationRepository registry = new ClientRegistrationRepository(registrations);
        assertThat(registry.findDefault()).isSameAs(clientRegistration);
    }

    @Test
    public void shouldFailToGetDefault() throws Exception {
        final ClientRegistrationRepository registry = new ClientRegistrationRepository();
        assertThat(registry.findDefault()).isNull();
    }

    @Test
    public void shouldNotNeedNascarPage() {
        final ClientRegistrationRepository registry = new ClientRegistrationRepository(registrations);
        assertThat(registry.needsNascarPage()).isFalse();
    }

    @Test
    public void shouldNeedNascarPageIfEmptyRegistrations() {
        final ClientRegistrationRepository registry = new ClientRegistrationRepository();
        assertThat(registry.needsNascarPage()).isTrue();
    }

    @Test
    public void shouldNeedNascarPageIfMultipleRegistrations() throws Exception {
        final ClientRegistrationRepository registry = new ClientRegistrationRepository(registrations);
        registry.add(buildClientRegistration("RockShop", registrationHandler));
        assertThat(registry.needsNascarPage()).isTrue();
    }

    @Test
    public void shouldFindReturnsNull() {
        final ClientRegistrationRepository registry = new ClientRegistrationRepository(registrations);
        assertThat(registry.findByName((String) null)).isNull();
        assertThat(registry.findByIssuer((Issuer) null)).isNull();
    }

    @Test
    public void shouldFindByName() {
        final ClientRegistrationRepository registry = new ClientRegistrationRepository();
        registry.add(clientRegistration);
        assertThat(registry.findByName("unknown")).isNull();
        assertThat(registry.findByName("ForgeShop")).isSameAs(clientRegistration);
    }

    @Test
    public void shouldFindByIssuer() throws Exception {
        final ClientRegistrationRepository registry = new ClientRegistrationRepository();
        registry.add(clientRegistration);
        assertThat(registry.findByIssuer(buildIssuerWithoutWellKnownEndpoint("unknown"))).isNull();
        assertThat(registry.findByIssuer(issuer)).isSameAs(clientRegistration);
    }
}
