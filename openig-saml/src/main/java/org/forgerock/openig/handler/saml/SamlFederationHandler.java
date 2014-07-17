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
package org.forgerock.openig.handler.saml;

import static java.lang.String.format;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.openig.config.Environment;
import org.forgerock.openig.handler.GenericHandler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.NestedHeaplet;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.log.LogTimer;

import com.sun.identity.common.ShutdownManager;

/**
 * The federation servlet adapter.
 */
public class SamlFederationHandler extends GenericHandler {

    private HttpServlet servlet;

    private static final long serialVersionUID = 1L;

    @Override
    public void handle(Exchange exchange) throws HandlerException, IOException {
        final LogTimer timer = logger.getTimer().start();
        try {
            servlet.service(adaptRequest(exchange), adaptResponse(exchange));
        } catch (ServletException e) {
            throw new HandlerException(e);
        } finally {
            timer.stop();
        }
    }

    private static HttpServletResponse adaptResponse(Exchange exchange) {
        return (HttpServletResponse) exchange.get(HttpServletResponse.class.getName());
    }

    private static HttpServletRequest adaptRequest(Exchange exchange) {
        HttpServletRequest request = (HttpServletRequest) exchange.get(HttpServletRequest.class.getName());
        return new RequestAdapter(request, exchange);
    }

    /**
     * Reads the actual federation servlet from the JSON configuration file.
     */
    public static class Heaplet extends NestedHeaplet {
        @Override
        public Object create() throws HeapException {
            final Map<String, String> attributeMapping = new HashMap<String, String>();
            JsonValue mappings = config.get("assertionMapping").expect(Map.class);
            if (mappings != null) {
                for (String key : mappings.keys()) {
                    attributeMapping.put(key, mappings.get(key).asString());
                }
            }
            final String authnContextDelimiter = config.get("authnContextDelimiter").defaultTo("|").asString();
            final String authnContext = config.get("authnContext").asString();
            final String redirectURI = config.get("redirectURI").asString();
            final String logoutURI = config.get("logoutURI").asString();
            // Give subjectMapping and sessionIndexMapping a default value as they are needed when doing SP initiated
            // SLO
            final String subjectMapping = config.get("subjectMapping").defaultTo("subjectMapping").asString();
            final String sessionIndexMapping = config.get("sessionIndexMapping").defaultTo("sessionIndexMapping")
                    .asString();
            final String assertionConsumerEndpoint = config.get("assertionConsumerEndpoint")
                    .defaultTo("fedletapplication").asString();
            final String sPinitiatedSSOEndpoint = config.get("SPinitiatedSSOEndpoint").defaultTo("SPInitiatedSSO")
                    .asString();
            final String singleLogoutEndpoint = config.get("singleLogoutEndpoint").defaultTo("fedletSlo").asString();
            final String singleLogoutEndpointSoap = config.get("singleLogoutEndpointSoap").defaultTo("fedletSloSoap")
                    .asString();
            final String sPinitiatedSLOEndpoint = config.get("SPinitiatedSLOEndpoint").defaultTo("SPInitiatedSLO")
                    .asString();
            /*
             * Get the gateway configuration directory and set it as a system property to override the default openFed
             * location. Federation config files will reside in the SAML directory.
             */
            Environment environment = (Environment) heap.get("Environment");
            String samlDirectory = new File(environment.getBaseDirectory(), "SAML").getPath();
            logger.info(format("FederationServlet init directory: %s", samlDirectory));
            Properties p = System.getProperties();
            p.setProperty("com.sun.identity.fedlet.home", samlDirectory);
            System.setProperties(p);

            final SamlFederationHandler handler = new SamlFederationHandler();
            handler.servlet = new FederationServlet(attributeMapping, subjectMapping, authnContextDelimiter,
                    authnContext, sessionIndexMapping, redirectURI, logoutURI, assertionConsumerEndpoint,
                    sPinitiatedSSOEndpoint, singleLogoutEndpoint, singleLogoutEndpointSoap, sPinitiatedSLOEndpoint,
                    logger);

            return handler;
        }

        @Override
        public void destroy() {
            // Automatically shutdown the fedlet
            ShutdownManager manager = ShutdownManager.getInstance();
            if (manager.acquireValidLock()) {
                try {
                    manager.shutdown();
                } finally {
                    manager.releaseLockAndNotify();
                }
            }
            super.destroy();
        }
    }
}
