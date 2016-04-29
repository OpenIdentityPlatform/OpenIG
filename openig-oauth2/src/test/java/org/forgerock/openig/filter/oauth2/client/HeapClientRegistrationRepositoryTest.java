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

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.openig.filter.oauth2.client.OAuth2TestUtils.buildClientRegistration;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import java.util.LinkedList;
import java.util.List;

import org.forgerock.http.Handler;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.log.Logger;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class HeapClientRegistrationRepositoryTest {

    private static final String DEFAULT_ISSUER_NAME = "OpenAM";
    private static final String FORGESHOP_CLIENT_NAME = "ForgeShop";
    private static final String HEAPSHOP_CLIENT_NAME = "HeapShop";

    private ClientRegistration forgeShopClientRegistration;
    private ClientRegistration heapShopClientRegistration;

    private HeapImpl heap;
    private List<ClientRegistration> registrations;

    @Mock
    private Logger logger;

    @Mock
    private Handler registrationHandler;

    @BeforeMethod
    public void setUp() throws Exception {
        initMocks(this);
        forgeShopClientRegistration = buildClientRegistration(FORGESHOP_CLIENT_NAME,
                                                              registrationHandler,
                                                              DEFAULT_ISSUER_NAME);
        heapShopClientRegistration = buildClientRegistration(HEAPSHOP_CLIENT_NAME,
                                                             registrationHandler,
                                                             "customIssuer");
        registrations = new LinkedList<>(singletonList(forgeShopClientRegistration));

        heap = spy(HeapUtilsTest.buildDefaultHeap());
        heap.put(HEAPSHOP_CLIENT_NAME, heapShopClientRegistration);
    }

    @SuppressWarnings("unused")
    @Test(expectedExceptions = NullPointerException.class)
    public void shouldNotAllowNullListOfClientRegistrations() {
        new HeapClientRegistrationRepository(null, heap, logger);
    }

    @Test
    public void shouldFindByName() throws Exception {
        final ClientRegistrationRepository registry = new HeapClientRegistrationRepository(registrations, heap, logger);
        assertThat(registry.findByName(FORGESHOP_CLIENT_NAME)).isSameAs(forgeShopClientRegistration);
        verify(heap, never()).get(FORGESHOP_CLIENT_NAME, ClientRegistration.class);
    }

    @Test
    public void shouldFindByNameSearchesInHeap() throws Exception {
        // Given
        final ClientRegistrationRepository registry = new HeapClientRegistrationRepository(registrations, heap, logger);

        // When
        final ClientRegistration heapShopClient = registry.findByName(HEAPSHOP_CLIENT_NAME);

        // Then
        assertThat(heapShopClient).isSameAs(heapShopClientRegistration);
        verify(heap).get(HEAPSHOP_CLIENT_NAME, ClientRegistration.class);
    }

    @Test
    public void shouldFindByNameCallsHeapOnlyOnce() throws Exception {
        // Given
        final ClientRegistrationRepository registry = new HeapClientRegistrationRepository(registrations, heap, logger);

        // When
        assertThat(registry.findByName(HEAPSHOP_CLIENT_NAME))
            .isSameAs(registry.findByName(HEAPSHOP_CLIENT_NAME))
            .isSameAs(heapShopClientRegistration);

        // Then
        verify(heap).get(HEAPSHOP_CLIENT_NAME, ClientRegistration.class);
    }

    @Test
    public void shouldFindByNameNotFoundReturnsNull() throws Exception {
        final ClientRegistrationRepository registry = new HeapClientRegistrationRepository(registrations, heap, logger);

        assertThat(registry.findByName("unknown")).isNull();
        verify(heap).get("unknown", ClientRegistration.class);
    }
}
