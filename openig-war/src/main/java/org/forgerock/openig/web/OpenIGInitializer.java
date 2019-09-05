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
 * Copyright 2016 ForgeRock AS.
 */

package org.forgerock.openig.web;

import static org.forgerock.json.JsonValueFunctions.enumConstant;
import static org.forgerock.openig.http.GatewayEnvironment.BASE_SYSTEM_PROPERTY;
import static org.forgerock.openig.http.RunMode.EVALUATION;
import static org.forgerock.openig.http.RunMode.PRODUCTION;
import static org.forgerock.openig.util.JsonValues.evaluated;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;

import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;

import org.forgerock.http.servlet.HttpFrameworkServlet;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.config.Environment;
import org.forgerock.openig.http.AdminHttpApplication;
import org.forgerock.openig.http.GatewayEnvironment;
import org.forgerock.openig.http.GatewayHttpApplication;
import org.forgerock.openig.http.RunMode;
import org.forgerock.openig.util.JsonValues;
import org.forgerock.util.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.util.StatusPrinter;

/**
 * This class is called automatically from the JEE container and initializes the whole OpenIG product.
 */
public class OpenIGInitializer implements ServletContainerInitializer {

    private static final Logger logger = LoggerFactory.getLogger(OpenIGInitializer.class);

    private Environment environment;

    /**
     * Default constructor called by the Servlet Framework.
     */
    public OpenIGInitializer() {
        this(new GatewayEnvironment());
    }

    /**
     * Constructor for tests.
     */
    @VisibleForTesting
    OpenIGInitializer(final Environment environment) {
        this.environment = environment;
    }

    @Override
    public void onStartup(final Set<Class<?>> classes, final ServletContext context) throws ServletException {
        try {
            //setupLogSystem();

            logger.info("OpenIG base directory : {}", environment.getBaseDirectory());

            URL adminConfigURL = selectConfigurationUrl("admin.json");
            JsonValue adminConfig = JsonValues.readJson(adminConfigURL);

            // Read OpenIG mode (will trigger exceptions if unknown values are used)
            RunMode mode = adminConfig.get("mode")
                                      .as(evaluated())
                                      .defaultTo(EVALUATION.name())
                                      .as(enumConstant(RunMode.class));

            String adminPrefix = adminConfig.get("prefix").defaultTo("openig").asString();

            // Expose the studio only in evaluation mode
            AdminHttpApplication admin;
            if (EVALUATION.equals(mode)) {
                logger.warn("The product is running in {} mode - by default, all endpoints are open and accessible. "
                            + "Do not use this mode for a production environment. To prevent this message, add the "
                            + "top-level attribute '\"mode\": \"{}\"' to '${openig.base}/config/admin.json.'",
                            EVALUATION.name(),
                            PRODUCTION.name());
                admin = new UiAdminHttpApplication(adminPrefix, adminConfig, environment, mode);
            } else {
                admin = new AdminHttpApplication(adminPrefix, adminConfig, environment, mode);
            }

            URL gatewayConfigURL = selectConfigurationUrl("config.json");
            GatewayHttpApplication gateway = new GatewayHttpApplication(environment,
                                                                        JsonValues.readJson(gatewayConfigURL),
                                                                        admin.getEndpointRegistry(),
                                                                        mode);

            ServletRegistration.Dynamic gwRegistration = context.addServlet("Gateway",
                                                                            new HttpFrameworkServlet(gateway));
            gwRegistration.setLoadOnStartup(1);
            gwRegistration.setAsyncSupported(true);
            gwRegistration.addMapping("/*");

            // Enable it if requested
            ServletRegistration.Dynamic adminRegistration = context.addServlet("Admin",
                                                                               new HttpFrameworkServlet(admin));
            adminRegistration.setLoadOnStartup(1);
            adminRegistration.setAsyncSupported(true);
            adminRegistration.addMapping("/" + adminPrefix + "/*");
        } catch (Exception e) {
            throw new ServletException("Cannot start OpenIG", e);
        }
    }

    @VisibleForTesting
    URL selectConfigurationUrl(final String filename) throws MalformedURLException {
        final File configuration = new File(environment.getConfigDirectory(), filename);
        final URL configurationURL;
        if (configuration.canRead()) {
            logger.info("Reading the configuration from {}", configuration.getAbsolutePath());
            configurationURL = configuration.toURI().toURL();
        } else {
            logger.info("{} not readable, using OpenIG's default-{}", configuration.getAbsolutePath(), filename);
            configurationURL = OpenIGInitializer.class.getResource("default-" + filename);
        }
        return configurationURL;
    }

    private void setupLogSystem() {
        // Assume SLF4J is bound to logback in the current environment
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

        try {
            URL logBackXml = findLogBackXml();

            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(context);
            // Clear any previous configuration, e.g. default configuration
            context.reset();
            context.putProperty(BASE_SYSTEM_PROPERTY, environment.getBaseDirectory().getAbsolutePath());
            configurator.doConfigure(logBackXml);
        } catch (JoranException | MalformedURLException je) {
            // StatusPrinter will handle this
        }
        StatusPrinter.printInCaseOfErrorsOrWarnings(context);
    }

    private URL findLogBackXml() throws MalformedURLException {
        final File logbackXml = new File(environment.getConfigDirectory(), "logback.xml");
        if (logbackXml.canRead()) {
            return logbackXml.toURI().toURL();
        }
        return getClass().getResource("logback.xml");
    }
}
