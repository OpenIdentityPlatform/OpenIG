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
 * Copyright 2014-2016 ForgeRock AS.
 */
package org.forgerock.openig.ldap;

import static org.forgerock.opendj.ldap.Connections.newCachedConnectionPool;
import static org.forgerock.opendj.ldap.LDAPConnectionFactory.HEARTBEAT_ENABLED;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.services.TransactionId;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.TransactionIdContext;
import org.forgerock.util.Option;
import org.forgerock.util.Options;

/**
 * This class acts as a simplified access point into the OpenDJ LDAP SDK. Whilst
 * it is possible for scripts to access the OpenDJ LDAP SDK APIs directly, this
 * class simplifies the most common use cases by exposes fields and methods for:
 * <ul>
 * <li>creating and caching LDAP connections
 * <li>parsing DNs and LDAP filters
 * <li>simple access to LDAP scopes.
 * </ul>
 */
public final class LdapClient {

    /**
     * The option to pass the TransactionId to LdapConnection.
     */
    public static final Option<TransactionId> TRANSACTIONID_OPTION = Option.of(TransactionId.class, null);

    private static final LdapClient INSTANCE = new LdapClient();

    /**
     * Returns the unique instance of an {@code LdapClient}.
     *
     * @return The unique instance of an {@code LdapClient}.
     */
    public static LdapClient getInstance() {
        return INSTANCE;
    }

    /**
     * Setup the default options to create a LdapClient and adds the transactionId if any in the context's chain.
     * @param context the context's chain
     * @return the default options with the current transactionId set if any in the context's chain
     */
    public static Options defaultOptions(Context context) {
        Options defaultOptions = Options.defaultOptions();

        if (context.containsContext(TransactionIdContext.class)) {
            TransactionIdContext txContext = context.asContext(TransactionIdContext.class);
            defaultOptions.set(TRANSACTIONID_OPTION, txContext.getTransactionId());
        }

        return defaultOptions;
    }


    private final ConcurrentMap<String, ConnectionFactory> factories =
            new ConcurrentHashMap<>();

    /**
     * A map containing the LDAP scopes making it easier to specify scopes
     * within scripts where Maps are exposed as properties, e.g. in Groovy the
     * sub-tree scope may be specified using the value "ldap.scope.sub".
     */
    private final Map<String, SearchScope> scope;

    private LdapClient() {
        final Map<String, SearchScope> map = new HashMap<>(4);
        for (final SearchScope scope : SearchScope.values()) {
            map.put(scope.toString(), scope);
        }
        scope = Collections.unmodifiableMap(map);
    }

    /**
     * Returns the {@link SearchScope} available.
     * @return the {@link SearchScope} available.
     */
    public Map<String, SearchScope> getScope() {
        return scope;
    }

    /**
     * Returns an LDAP connection for the specified LDAP server. The returned
     * connection must be closed once the caller has completed its transaction.
     * Connections are cached between calls using a connection pool.
     *
     * @param host The LDAP server host name.
     * @param port The LDAP server port.
     * @return An LDAP connection for the specified LDAP server.
     * @throws LdapException If an error occurred while connecting to the LDAP server.
     */
    public LdapConnection connect(final String host, final int port) throws LdapException {
        return connect(host, port, Options.defaultOptions());
    }

    /**
     * Returns an LDAP connection for the specified LDAP server using the
     * provided LDAP options. The returned connection must be closed once the
     * caller has completed its transaction. Connections are cached between
     * calls using a connection pool. The LDAP options may be used for
     * configuring SSL parameters and timeouts.
     * <p>
     * NOTE: if a connection has already been obtained to the specified LDAP
     * server then a cached connection will be returned and the LDAP options
     * will be ignored.
     *
     * @param host The LDAP server host name.
     * @param port The LDAP server port.
     * @param options The LDAP options.
     * @return An LDAP connection for the specified LDAP server.
     * @throws LdapException If an error occurred while connecting to the LDAP server.
     */
    public LdapConnection connect(final String host, final int port, final Options options)
            throws LdapException {
        final ConnectionFactory factory = getConnectionFactory(host, port, options);
        return new LdapConnection(factory.getConnection(), options.get(TRANSACTIONID_OPTION));
    }

    /**
     * Formats an LDAP distinguished name using the provided template and
     * attribute values. Values will be safely escaped in order to avoid
     * potential injection attacks.
     *
     * @param template The DN template.
     * @param attributeValues The attribute values to be substituted into the template.
     * @return The formatted template parsed as a {@code DN}.
     * @throws org.forgerock.i18n.LocalizedIllegalArgumentException If the formatted template is not a valid LDAP string
     * representation of a DN.
     * @see DN#format(String, Object...)
     */
    public String dn(final String template, final Object... attributeValues) {
        return DN.format(template, attributeValues).toString();
    }

    /**
     * Formats an LDAP filter using the provided template and assertion values.
     * Values will be safely escaped in order to avoid potential injection
     * attacks.
     *
     * @param template The filter template.
     * @param assertionValues The assertion values to be substituted into the template.
     * @return The formatted template parsed as a {@code Filter}.
     * @throws org.forgerock.i18n.LocalizedIllegalArgumentException If the formatted template is not a valid LDAP string
     * representation of a filter.
     * @see Filter#format(String, Object...)
     */
    public String filter(final String template, final Object... assertionValues) {
        return Filter.format(template, assertionValues).toString();
    }

    private ConnectionFactory getConnectionFactory(final String host, final int port,
                                                   final Options options) {
        final String key = host + ":" + port;
        ConnectionFactory factory = factories.get(key);
        if (factory == null) {
            synchronized (factories) {
                factory = factories.get(key);
                if (factory == null) {
                    options.set(HEARTBEAT_ENABLED, true);
                    factory = newCachedConnectionPool(new LDAPConnectionFactory(host, port, options));
                    factories.put(key, factory);
                }
            }
        }
        return factory;
    }

    @Override
    protected void finalize() throws Throwable {
        for (ConnectionFactory factory : factories.values()) {
            factory.close();
        }
        factories.clear();
        super.finalize();
    }
}
