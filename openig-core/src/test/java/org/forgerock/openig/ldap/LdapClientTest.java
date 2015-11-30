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
 * Copyright 2015 ForgeRock AS.
 */

package org.forgerock.openig.ldap;

import static org.assertj.core.api.Assertions.assertThat;

import org.forgerock.opendj.ldap.AuthenticationException;
import org.forgerock.opendj.ldap.Connections;
import org.forgerock.opendj.ldap.LDAPClientContext;
import org.forgerock.opendj.ldap.LDAPListener;
import org.forgerock.opendj.ldap.MemoryBackend;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldif.LDIFEntryReader;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class LdapClientTest {

    private LDAPListener listener;
    private LdapClient client;

    @BeforeMethod
    public void setUp() throws Exception {
        // Create mock LDAP server with a single user.
        MemoryBackend backend = new MemoryBackend(new LDIFEntryReader(
                "dn:",
                "objectClass: top",
                "objectClass: extensibleObject",
                "",
                "dn: dc=com",
                "objectClass: domain",
                "objectClass: top",
                "dc: com",
                "",
                "dn: dc=example,dc=com",
                "objectClass: domain",
                "objectClass: top",
                "dc: example",
                "",
                "dn: ou=people,dc=example,dc=com",
                "objectClass: organizationalUnit",
                "objectClass: top",
                "ou: people",
                "",
                "dn: uid=bjensen,ou=people,dc=example,dc=com",
                "objectClass: top",
                "objectClass: person",
                "objectClass: organizationalPerson",
                "objectClass: inetOrgPerson",
                "cn: Barbara",
                "sn: Jensen",
                "uid: bjensen",
                "description: test user",
                "userPassword: password"));

        listener = new LDAPListener(0, Connections.<LDAPClientContext>newServerConnectionFactory(backend));
        client = LdapClient.getInstance();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
        listener.close();
        // Force close of connection factories
        client.finalize();
    }

    @Test
    public void shouldFindAnEntryInLdapServer() throws Exception {

        LdapConnection connection = client.connect(listener.getHostName(), listener.getPort());

        String filter = client.filter("(uid=%s)", "bjensen");
        SearchResultEntry resultEntry = connection.searchSingleEntry("ou=people,dc=example,dc=com",
                                                                     SearchScope.WHOLE_SUBTREE,
                                                                     filter);

        assertThat(resultEntry).isNotNull();
        assertThat(resultEntry.getAttribute("description").firstValueAsString()).isEqualTo("test user");
    }

    @Test
    public void shouldBindToLdapServer() throws Exception {

        LdapConnection connection = client.connect(listener.getHostName(), listener.getPort());

        BindResult bindResult = connection.bind("uid=bjensen,ou=people,dc=example,dc=com", "password".toCharArray());
        assertThat(bindResult.getResultCode()).isEqualTo(ResultCode.SUCCESS);
    }

    @Test(expectedExceptions = AuthenticationException.class)
    public void shouldFailToBindBecauseOfInvalidCredentials() throws Exception {

        LdapConnection connection = client.connect(listener.getHostName(), listener.getPort());

        connection.bind("uid=bjensen,ou=people,dc=example,dc=com", "wrong-value".toCharArray());
    }
}
