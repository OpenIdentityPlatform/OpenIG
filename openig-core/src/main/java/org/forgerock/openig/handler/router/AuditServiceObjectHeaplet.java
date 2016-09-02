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
* Copyright 2015-2016 ForgeRock AS.
*/
package org.forgerock.openig.handler.router;

import static org.forgerock.audit.AuditServiceBuilder.newAuditService;
import static org.forgerock.audit.json.AuditJsonConfig.registerHandlerToService;
import static org.forgerock.http.HttpApplication.LOGGER;

import org.forgerock.audit.AuditException;
import org.forgerock.audit.AuditService;
import org.forgerock.audit.AuditServiceBuilder;
import org.forgerock.audit.AuditServiceConfiguration;
import org.forgerock.audit.DependencyProvider;
import org.forgerock.audit.json.AuditJsonConfig;
import org.forgerock.audit.providers.DefaultLocalHostNameProvider;
import org.forgerock.audit.providers.LocalHostNameProvider;
import org.forgerock.audit.providers.ProductInfoProvider;
import org.forgerock.http.Client;
import org.forgerock.http.Handler;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.ServiceUnavailableException;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.Heap;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.util.annotations.VisibleForTesting;

/**
 * Constructs an {@link AuditService} through an {@link Heaplet}.
 */
/** Creates and initializes an AuditService in a heap environment. */
public class AuditServiceObjectHeaplet extends GenericHeaplet {

    private AuditService auditService;

    @Override
    public Object create() throws HeapException {
        try {
            auditService = buildAuditService(config.as(evaluatedWithHeapProperties()));
            return auditService;
        } catch (AuditException ex) {
            throw new HeapException(ex);
        }
    }

    @Override
    public void start() throws HeapException {
        super.start();
        if (auditService != null) {
            try {
                auditService.startup();
            } catch (ServiceUnavailableException ex) {
                throw new HeapException(ex);
            }
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        if (auditService != null) {
            auditService.shutdown();
        }
    }

    /** Loads the audit service configuration from JSON. */
    private AuditService buildAuditService(JsonValue config) throws AuditException {
        final JsonValue auditServiceConfig = config.get("config");

        final AuditServiceConfiguration auditServiceConfiguration;
        if (auditServiceConfig.isNotNull()) {
            auditServiceConfiguration = AuditJsonConfig.parseAuditServiceConfiguration(auditServiceConfig);
        } else {
            auditServiceConfiguration = new AuditServiceConfiguration();
        }
        AuditServiceBuilder auditServiceBuilder = newAuditService();
        auditServiceBuilder.withConfiguration(auditServiceConfiguration);
        auditServiceBuilder.withDependencyProvider(new GatewayDependencyProvider(heap));

        final ClassLoader classLoader = this.getClass().getClassLoader();
        for (final JsonValue handlerConfig : config.get("event-handlers")) {
            try {
                registerHandlerToService(handlerConfig, auditServiceBuilder, classLoader);
            } catch (Exception ex) {
                LOGGER.error("Unable to register handler defined by config: " + handlerConfig, ex);
            }
        }

        return auditServiceBuilder.build();
    }

    @VisibleForTesting
    static class GatewayDependencyProvider implements DependencyProvider {

        private final LocalHostNameProvider localHostNameProvider = new DefaultLocalHostNameProvider();

        private final ProductInfoProvider productInfoProvider = new ProductInfoProvider() {

            @Override
            public String getProductName() {
                return "OpenIG";
            }
        };
        private final Heap heap;

        GatewayDependencyProvider(Heap heap) {
            this.heap = heap;
        }

        @Override
        public <T> T getDependency(Class<T> type) throws ClassNotFoundException {
            if (LocalHostNameProvider.class.equals(type)) {
                return type.cast(localHostNameProvider);
            } else if (ProductInfoProvider.class.equals(type)) {
                return type.cast(productInfoProvider);
            } else if (Client.class.equals(type)) {
                try {
                    Handler handler = heap.get("ElasticsearchClientHandler", Handler.class);
                    if (handler != null) {
                        return type.cast(new Client(handler));
                    }
                } catch (HeapException e) {
                    LOGGER.error("An error occurred while looking for the ElasticsearchClientHandler", e);
                    throw new ClassNotFoundException(type.getName(), e);
                }
            }
            throw new ClassNotFoundException(type.getName());
        }
    }
}
