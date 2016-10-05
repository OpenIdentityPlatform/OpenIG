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

package org.forgerock.openig.ldap;

import static org.assertj.core.api.Assertions.assertThat;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Request;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.services.TransactionId;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.forgerock.opendj.ldap.controls.TransactionIdControl;


@SuppressWarnings("javadoc")
public class LdapConnectionTest {

    private LdapConnection ldapConnection;

    @Mock
    private Connection connection;

    @BeforeMethod
    private void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        ldapConnection = new LdapConnection(connection, new TransactionId("foo"));
    }

    @Test
    public void shouldUseTransactionIdControlInAddRequest() throws Exception {
        AddRequest addRequest = Requests.newAddRequest("dc=example,dc=com");
        ldapConnection.add(addRequest);

        assertLdapRequest(addRequest);
    }

    @Test
    public void shouldUseTransactionIdControlInModifyRequest() throws Exception {
        ModifyRequest modifyRequest = Requests.newModifyRequest("dc=example,dc=com");
        ldapConnection.modify(modifyRequest);

        assertLdapRequest(modifyRequest);
    }

    @Test
    public void shouldUseTransactionIdControlInDeleteRequest() throws Exception {
        DeleteRequest deleteRequest = Requests.newDeleteRequest("dc=example,dc=com");
        ldapConnection.delete(deleteRequest);

        assertLdapRequest(deleteRequest);
    }

    @Test
    public void shouldUseTransactionIdControlInCompareRequest() throws Exception {
        CompareRequest compareRequest = Requests.newCompareRequest("dc=example,dc=com", "user", "user.0");
        ldapConnection.compare(compareRequest);

        assertLdapRequest(compareRequest);
    }

    @Test
    public void shouldUseTransactionIdControlInSearchRequest() throws Exception {
        SearchRequest searchRequest = Requests.newSearchRequest("dc=example,dc=com",
                                                                SearchScope.WHOLE_SUBTREE,
                                                                "(objectclass=inetOrgPerson)",
                                                                "cn");
        ldapConnection.search(searchRequest);

        assertLdapRequest(searchRequest);
    }

    @Test
    public void shouldCreateSubTransactionForEachRequest() throws Exception {
        TransactionIdControl control;
        // Given : 1st request
        AddRequest addRequest = Requests.newAddRequest("dc=example,dc=com");

        // When : 1st request
        ldapConnection.add(addRequest);
        control = addRequest.getControl(TransactionIdControl.DECODER, new DecodeOptions());

        // Then : 1st request
        assertThat(control.getValue().toASCIIString()).isEqualTo("foo/0");

        // Given : 2nd request
        SearchRequest searchRequest = Requests.newSearchRequest("dc=example,dc=com",
                                                                SearchScope.WHOLE_SUBTREE,
                                                                "(objectclass=inetOrgPerson)",
                                                                "cn");
        // When : 2nd request
        ldapConnection.search(searchRequest);
        control = searchRequest.getControl(TransactionIdControl.DECODER, new DecodeOptions());

        // Then : 2nd request
        assertThat(control.getValue().toASCIIString()).isEqualTo("foo/1");
    }

    private void assertLdapRequest(Request request) throws Exception {
        assertThat(request.containsControl(TransactionIdControl.OID));
        TransactionIdControl control = request.getControl(TransactionIdControl.DECODER, new DecodeOptions());
        assertThat(control.getValue().toASCIIString()).isEqualTo("foo/0");
    }

}
