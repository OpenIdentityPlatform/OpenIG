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
 * Copyright 2014-2015 ForgeRock AS.
 */

package org.forgerock.openig.alias;

import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;

import org.forgerock.openig.audit.monitor.MonitorEndpointHandler;
import org.forgerock.openig.decoration.baseuri.BaseUriDecorator;
import org.forgerock.openig.decoration.capture.CaptureDecorator;
import org.forgerock.openig.decoration.timer.TimerDecorator;
import org.forgerock.openig.filter.AssignmentFilter;
import org.forgerock.openig.filter.Chain;
import org.forgerock.openig.filter.CookieFilter;
import org.forgerock.openig.filter.CryptoHeaderFilter;
import org.forgerock.openig.filter.EntityExtractFilter;
import org.forgerock.openig.filter.FileAttributesFilter;
import org.forgerock.openig.filter.HeaderFilter;
import org.forgerock.openig.filter.HttpBasicAuthFilter;
import org.forgerock.openig.filter.LocationHeaderFilter;
import org.forgerock.openig.filter.PasswordReplayFilter;
import org.forgerock.openig.filter.ScriptableFilter;
import org.forgerock.openig.filter.SqlAttributesFilter;
import org.forgerock.openig.filter.StaticRequestFilter;
import org.forgerock.openig.filter.SwitchFilter;
import org.forgerock.openig.filter.ThrottlingFilter;
import org.forgerock.openig.handler.ClientHandler;
import org.forgerock.openig.handler.DesKeyGenHandler;
import org.forgerock.openig.handler.DispatchHandler;
import org.forgerock.openig.handler.ScriptableHandler;
import org.forgerock.openig.handler.SequenceHandler;
import org.forgerock.openig.handler.StaticResponseHandler;
import org.forgerock.openig.handler.WelcomeHandler;
import org.forgerock.openig.handler.router.RouterHandler;
import org.forgerock.openig.io.TemporaryStorage;
import org.forgerock.openig.jwt.JwtSessionManager;
import org.forgerock.openig.log.ConsoleLogSink;
import org.forgerock.openig.log.FileLogSink;
import org.forgerock.openig.log.NullLogSink;
import org.forgerock.openig.security.TrustAllManager;

/**
 * Register all the aliases supported by the {@literal openig-core} module.
 */
public class CoreClassAliasResolver implements ClassAliasResolver {
    private static final Map<String, Class<?>> ALIASES = new HashMap<>();

    static {
        ALIASES.put("AssignmentFilter", AssignmentFilter.class);
        ALIASES.put("BaseUriDecorator", BaseUriDecorator.class);
        ALIASES.put("CaptureDecorator", CaptureDecorator.class);
        ALIASES.put("Chain", Chain.class);
        ALIASES.put("ClientHandler", ClientHandler.class);
        ALIASES.put("ConsoleLogSink", ConsoleLogSink.class);
        ALIASES.put("CookieFilter", CookieFilter.class);
        ALIASES.put("CryptoHeaderFilter", CryptoHeaderFilter.class);
        ALIASES.put("DesKeyGenHandler", DesKeyGenHandler.class);
        ALIASES.put("DispatchHandler", DispatchHandler.class);
        ALIASES.put("EntityExtractFilter", EntityExtractFilter.class);
        ALIASES.put("FileAttributesFilter", FileAttributesFilter.class);
        ALIASES.put("FileLogSink", FileLogSink.class);
        ALIASES.put("HeaderFilter", HeaderFilter.class);
        ALIASES.put("HttpBasicAuthFilter", HttpBasicAuthFilter.class);
        ALIASES.put("JwtSessionFactory", JwtSessionManager.class);
        ALIASES.put("JwtSession", JwtSessionManager.class);
        ALIASES.put("KeyManager", KeyManager.class);
        ALIASES.put("KeyStore", KeyStore.class);
        ALIASES.put("LocationHeaderFilter", LocationHeaderFilter.class);
        ALIASES.put("MonitorEndpointHandler", MonitorEndpointHandler.class);
        ALIASES.put("NullLogSink", NullLogSink.class);
        ALIASES.put("PasswordReplayFilter", PasswordReplayFilter.class);
        ALIASES.put("RedirectFilter", LocationHeaderFilter.class);
        ALIASES.put("Router", RouterHandler.class);
        ALIASES.put("RouterHandler", RouterHandler.class);
        ALIASES.put("ScriptableFilter", ScriptableFilter.class);
        ALIASES.put("ScriptableHandler", ScriptableHandler.class);
        ALIASES.put("SequenceHandler", SequenceHandler.class);
        ALIASES.put("SqlAttributesFilter", SqlAttributesFilter.class);
        ALIASES.put("StaticRequestFilter", StaticRequestFilter.class);
        ALIASES.put("StaticResponseHandler", StaticResponseHandler.class);
        ALIASES.put("SwitchFilter", SwitchFilter.class);
        ALIASES.put("TemporaryStorage", TemporaryStorage.class);
        ALIASES.put("ThrottlingFilter", ThrottlingFilter.class);
        ALIASES.put("TimerDecorator", TimerDecorator.class);
        ALIASES.put("TrustManager", TrustManager.class);
        ALIASES.put("TrustAllManager", TrustAllManager.class);
        ALIASES.put("WelcomeHandler", WelcomeHandler.class);
    }

    @Override
    public Class<?> resolve(final String alias) {
        return ALIASES.get(alias);
    }
}
