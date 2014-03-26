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
 * Copyright 2014 ForgeRock AS.
 */
package org.forgerock.openig.ldap;

import java.io.Closeable;
import java.util.Collection;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.Connections;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.EntryNotFoundException;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.MultipleEntriesFoundException;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.forgerock.opendj.ldif.ConnectionEntryReader;

/**
 * Provides an adapted view of an OpenDJ LDAP connection exposing only the
 * synchronous methods and protecting against future evolution of the
 * {@link Connection} interface (e.g. migration to Promises).
 */
public final class LdapConnection implements Closeable {
    private final Connection connection;

    LdapConnection(final Connection connection) {
        this.connection = connection;
    }

    /**
     * Adds an entry to the Directory Server using the provided add request.
     *
     * @param request
     *            The add request.
     * @return The result of the operation.
     * @throws ErrorResultException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws UnsupportedOperationException
     *             If this connection does not support add operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code request} was {@code null}.
     */
    public Result add(AddRequest request) throws ErrorResultException {
        return connection.add(request);
    }

    /**
     * Adds the provided entry to the Directory Server.
     * <p>
     * This method is equivalent to the following code:
     *
     * <pre>
     * AddRequest request = new AddRequest(entry);
     * connection.add(request);
     * </pre>
     *
     * @param entry
     *            The entry to be added.
     * @return The result of the operation.
     * @throws ErrorResultException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws UnsupportedOperationException
     *             If this connection does not support add operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code entry} was {@code null} .
     */
    public Result add(Entry entry) throws ErrorResultException {
        return connection.add(entry);
    }

    /**
     * Adds an entry to the Directory Server using the provided lines of LDIF.
     * <p>
     * This method is equivalent to the following code:
     *
     * <pre>
     * AddRequest request = new AddRequest(ldifLines);
     * connection.add(request);
     * </pre>
     *
     * @param ldifLines
     *            Lines of LDIF containing the an LDIF add change record or an
     *            LDIF entry record.
     * @return The result of the operation.
     * @throws ErrorResultException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws UnsupportedOperationException
     *             If this connection does not support add operations.
     * @throws LocalizedIllegalArgumentException
     *             If {@code ldifLines} was empty, or contained invalid LDIF, or
     *             could not be decoded using the default schema.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code ldifLines} was {@code null} .
     */
    public Result add(String... ldifLines) throws ErrorResultException {
        return connection.add(ldifLines);
    }

    /**
     * Authenticates to the Directory Server using the provided bind request.
     *
     * @param request
     *            The bind request.
     * @return The result of the operation.
     * @throws ErrorResultException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws UnsupportedOperationException
     *             If this connection does not support bind operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code request} was {@code null}.
     */
    public BindResult bind(BindRequest request) throws ErrorResultException {
        return connection.bind(request);
    }

    /**
     * Authenticates to the Directory Server using simple authentication and the
     * provided user name and password.
     * <p>
     * This method is equivalent to the following code:
     *
     * <pre>
     * BindRequest request = new SimpleBindRequest(name, password);
     * connection.bind(request);
     * </pre>
     *
     * @param name
     *            The distinguished name of the Directory object that the client
     *            wishes to bind as, which may be empty.
     * @param password
     *            The password of the Directory object that the client wishes to
     *            bind as, which may be empty.
     * @return The result of the operation.
     * @throws ErrorResultException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws LocalizedIllegalArgumentException
     *             If {@code name} could not be decoded using the default
     *             schema.
     * @throws UnsupportedOperationException
     *             If this connection does not support bind operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code name} or {@code password} was {@code null}.
     */
    public BindResult bind(String name, char[] password) throws ErrorResultException {
        return connection.bind(name, password);
    }

    /**
     * Releases any resources associated with this connection. For physical
     * connections to a Directory Server this will mean that an unbind request
     * is sent and the underlying socket is closed.
     * <p>
     * Other connection implementations may behave differently, and may choose
     * not to send an unbind request if its use is inappropriate (for example a
     * pooled connection will be released and returned to its connection pool
     * without ever issuing an unbind request).
     * <p>
     * This method is equivalent to the following code:
     *
     * <pre>
     * UnbindRequest request = new UnbindRequest();
     * connection.close(request);
     * </pre>
     *
     * Calling {@code close} on a connection that is already closed has no
     * effect.
     *
     * @see Connections#uncloseable(Connection)
     */
    @Override
    public void close() {
        connection.close();
    }

    /**
     * Compares an entry in the Directory Server using the provided compare
     * request.
     *
     * @param request
     *            The compare request.
     * @return The result of the operation.
     * @throws ErrorResultException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws UnsupportedOperationException
     *             If this connection does not support compare operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code request} was {@code null}.
     */
    public CompareResult compare(CompareRequest request) throws ErrorResultException {
        return connection.compare(request);
    }

    /**
     * Compares the named entry in the Directory Server against the provided
     * attribute value assertion.
     * <p>
     * This method is equivalent to the following code:
     *
     * <pre>
     * CompareRequest request = new CompareRequest(name, attributeDescription, assertionValue);
     * connection.compare(request);
     * </pre>
     *
     * @param name
     *            The distinguished name of the entry to be compared.
     * @param attributeDescription
     *            The name of the attribute to be compared.
     * @param assertionValue
     *            The assertion value to be compared.
     * @return The result of the operation.
     * @throws ErrorResultException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws LocalizedIllegalArgumentException
     *             If {@code name} or {@code AttributeDescription} could not be
     *             decoded using the default schema.
     * @throws UnsupportedOperationException
     *             If this connection does not support compare operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code name}, {@code attributeDescription}, or
     *             {@code assertionValue} was {@code null}.
     */
    public CompareResult compare(String name, String attributeDescription, String assertionValue)
            throws ErrorResultException {
        return connection.compare(name, attributeDescription, assertionValue);
    }

    /**
     * Deletes an entry from the Directory Server using the provided delete
     * request.
     *
     * @param request
     *            The delete request.
     * @return The result of the operation.
     * @throws ErrorResultException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws UnsupportedOperationException
     *             If this connection does not support delete operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code request} was {@code null}.
     */
    public Result delete(DeleteRequest request) throws ErrorResultException {
        return connection.delete(request);
    }

    /**
     * Deletes the named entry from the Directory Server.
     * <p>
     * This method is equivalent to the following code:
     *
     * <pre>
     * DeleteRequest request = new DeleteRequest(name);
     * connection.delete(request);
     * </pre>
     *
     * @param name
     *            The distinguished name of the entry to be deleted.
     * @return The result of the operation.
     * @throws ErrorResultException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws LocalizedIllegalArgumentException
     *             If {@code name} could not be decoded using the default
     *             schema.
     * @throws UnsupportedOperationException
     *             If this connection does not support delete operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code name} was {@code null}.
     */
    public Result delete(String name) throws ErrorResultException {
        return connection.delete(name);
    }

    /**
     * Deletes the named entry and all of its subordinates from the Directory
     * Server.
     * <p>
     * This method is equivalent to the following code:
     *
     * <pre>
     * DeleteRequest request = new DeleteRequest(name).addControl(
     * connection.delete(request);
     * </pre>
     *
     * @param name
     *            The distinguished name of the subtree base entry to be
     *            deleted.
     * @return The result of the operation.
     * @throws ErrorResultException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws LocalizedIllegalArgumentException
     *             If {@code name} could not be decoded using the default
     *             schema.
     * @throws UnsupportedOperationException
     *             If this connection does not support delete operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code name} was {@code null}.
     */
    public Result deleteSubtree(String name) throws ErrorResultException {
        return connection.deleteSubtree(name);
    }

    /**
     * Modifies an entry in the Directory Server using the provided modify
     * request.
     *
     * @param request
     *            The modify request.
     * @return The result of the operation.
     * @throws ErrorResultException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws UnsupportedOperationException
     *             If this connection does not support modify operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code request} was {@code null}.
     */
    public Result modify(ModifyRequest request) throws ErrorResultException {
        return connection.modify(request);
    }

    /**
     * Modifies an entry in the Directory Server using the provided lines of
     * LDIF.
     * <p>
     * This method is equivalent to the following code:
     *
     * <pre>
     * ModifyRequest request = new ModifyRequest(name, ldifChanges);
     * connection.modify(request);
     * </pre>
     *
     * @param ldifLines
     *            Lines of LDIF containing the a single LDIF modify change
     *            record.
     * @return The result of the operation.
     * @throws ErrorResultException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws UnsupportedOperationException
     *             If this connection does not support modify operations.
     * @throws LocalizedIllegalArgumentException
     *             If {@code ldifLines} was empty, or contained invalid LDIF, or
     *             could not be decoded using the default schema.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code ldifLines} was {@code null} .
     */
    public Result modify(String... ldifLines) throws ErrorResultException {
        return connection.modify(ldifLines);
    }

    /**
     * Renames an entry in the Directory Server using the provided modify DN
     * request.
     *
     * @param request
     *            The modify DN request.
     * @return The result of the operation.
     * @throws ErrorResultException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws UnsupportedOperationException
     *             If this connection does not support modify DN operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code request} was {@code null}.
     */
    public Result modifyDN(ModifyDNRequest request) throws ErrorResultException {
        return connection.modifyDN(request);
    }

    /**
     * Renames the named entry in the Directory Server using the provided new
     * RDN.
     * <p>
     * This method is equivalent to the following code:
     *
     * <pre>
     * ModifyDNRequest request = new ModifyDNRequest(name, newRDN);
     * connection.modifyDN(request);
     * </pre>
     *
     * @param name
     *            The distinguished name of the entry to be renamed.
     * @param newRDN
     *            The new RDN of the entry.
     * @return The result of the operation.
     * @throws ErrorResultException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws LocalizedIllegalArgumentException
     *             If {@code name} or {@code newRDN} could not be decoded using
     *             the default schema.
     * @throws UnsupportedOperationException
     *             If this connection does not support modify DN operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code name} or {@code newRDN} was {@code null}.
     */
    public Result modifyDN(String name, String newRDN) throws ErrorResultException {
        return connection.modifyDN(name, newRDN);
    }

    /**
     * Reads the named entry from the Directory Server.
     * <p>
     * If the requested entry is not returned by the Directory Server then the
     * request will fail with an {@link EntryNotFoundException}. More
     * specifically, this method will never return {@code null}.
     * <p>
     * This method is equivalent to the following code:
     *
     * <pre>
     * SearchRequest request =
     *         new SearchRequest(name, SearchScope.BASE_OBJECT, &quot;(objectClass=*)&quot;, attributeDescriptions);
     * connection.searchSingleEntry(request);
     * </pre>
     *
     * @param name
     *            The distinguished name of the entry to be read.
     * @param attributeDescriptions
     *            The names of the attributes to be included with the entry,
     *            which may be {@code null} or empty indicating that all user
     *            attributes should be returned.
     * @return The single search result entry returned from the search.
     * @throws ErrorResultException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws UnsupportedOperationException
     *             If this connection does not support search operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If the {@code name} was {@code null}.
     */
    public SearchResultEntry readEntry(DN name, String... attributeDescriptions)
            throws ErrorResultException {
        return connection.readEntry(name, attributeDescriptions);
    }

    /**
     * Reads the named entry from the Directory Server.
     * <p>
     * If the requested entry is not returned by the Directory Server then the
     * request will fail with an {@link EntryNotFoundException}. More
     * specifically, this method will never return {@code null}.
     * <p>
     * This method is equivalent to the following code:
     *
     * <pre>
     * SearchRequest request =
     *         new SearchRequest(name, SearchScope.BASE_OBJECT, &quot;(objectClass=*)&quot;, attributeDescriptions);
     * connection.searchSingleEntry(request);
     * </pre>
     *
     * @param name
     *            The distinguished name of the entry to be read.
     * @param attributeDescriptions
     *            The names of the attributes to be included with the entry.
     * @return The single search result entry returned from the search.
     * @throws ErrorResultException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws LocalizedIllegalArgumentException
     *             If {@code baseObject} could not be decoded using the default
     *             schema.
     * @throws UnsupportedOperationException
     *             If this connection does not support search operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If the {@code name} was {@code null}.
     */
    public SearchResultEntry readEntry(String name, String... attributeDescriptions)
            throws ErrorResultException {
        return connection.readEntry(name, attributeDescriptions);
    }

    /**
     * Searches the Directory Server using the provided search parameters. Any
     * matching entries returned by the search will be exposed through the
     * returned {@code ConnectionEntryReader}.
     * <p>
     * Unless otherwise specified, calling this method is equivalent to:
     *
     * <pre>
     * ConnectionEntryReader reader = new ConnectionEntryReader(this, request);
     * </pre>
     *
     * @param request
     *            The search request.
     * @return The result of the operation.
     * @throws UnsupportedOperationException
     *             If this connection does not support search operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code request} or {@code entries} was {@code null}.
     */
    public ConnectionEntryReader search(SearchRequest request) {
        return connection.search(request);
    }

    /**
     * Searches the Directory Server using the provided search request. Any
     * matching entries returned by the search will be added to {@code entries},
     * even if the final search result indicates that the search failed. Search
     * result references will be discarded.
     * <p>
     * <b>Warning:</b> Usage of this method is discouraged if the search request
     * is expected to yield a large number of search results since the entire
     * set of results will be stored in memory, potentially causing an
     * {@code OutOfMemoryError}.
     * <p>
     * This method is equivalent to the following code:
     *
     * <pre>
     * connection.search(request, entries, null);
     * </pre>
     *
     * @param request
     *            The search request.
     * @param entries
     *            The collection to which matching entries should be added.
     * @return The result of the operation.
     * @throws ErrorResultException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws UnsupportedOperationException
     *             If this connection does not support search operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code request} or {@code entries} was {@code null}.
     */
    public Result search(SearchRequest request, Collection<? super SearchResultEntry> entries)
            throws ErrorResultException {
        return connection.search(request, entries);
    }

    /**
     * Searches the Directory Server using the provided search request. Any
     * matching entries returned by the search will be added to {@code entries},
     * even if the final search result indicates that the search failed.
     * Similarly, search result references returned by the search will be added
     * to {@code references}.
     * <p>
     * <b>Warning:</b> Usage of this method is discouraged if the search request
     * is expected to yield a large number of search results since the entire
     * set of results will be stored in memory, potentially causing an
     * {@code OutOfMemoryError}.
     *
     * @param request
     *            The search request.
     * @param entries
     *            The collection to which matching entries should be added.
     * @param references
     *            The collection to which search result references should be
     *            added, or {@code null} if references are to be discarded.
     * @return The result of the operation.
     * @throws ErrorResultException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws UnsupportedOperationException
     *             If this connection does not support search operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code request} or {@code entries} was {@code null}.
     */
    public Result search(SearchRequest request, Collection<? super SearchResultEntry> entries,
            Collection<? super SearchResultReference> references) throws ErrorResultException {
        return connection.search(request, entries, references);
    }

    /**
     * Searches the Directory Server using the provided search parameters. Any
     * matching entries returned by the search will be exposed through the
     * {@code EntryReader} interface.
     * <p>
     * <b>Warning:</b> When using a queue with an optional capacity bound, the
     * connection will stop reading responses and wait if necessary for space to
     * become available.
     * <p>
     * This method is equivalent to the following code:
     *
     * <pre>
     * SearchRequest request = new SearchRequest(baseDN, scope, filter, attributeDescriptions);
     * connection.search(request, new LinkedBlockingQueue&lt;Response&gt;());
     * </pre>
     *
     * @param baseObject
     *            The distinguished name of the base entry relative to which the
     *            search is to be performed.
     * @param scope
     *            The scope of the search.
     * @param filter
     *            The filter that defines the conditions that must be fulfilled
     *            in order for an entry to be returned.
     * @param attributeDescriptions
     *            The names of the attributes to be included with each entry.
     * @return An entry reader exposing the returned entries.
     * @throws UnsupportedOperationException
     *             If this connection does not support search operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If the {@code baseObject}, {@code scope}, or {@code filter}
     *             were {@code null}.
     */
    public ConnectionEntryReader search(String baseObject, SearchScope scope, String filter,
            String... attributeDescriptions) {
        return connection.search(baseObject, scope, filter, attributeDescriptions);
    }

    /**
     * Searches the Directory Server for a single entry using the provided
     * search request.
     * <p>
     * If the requested entry is not returned by the Directory Server then the
     * request will fail with an {@link EntryNotFoundException}. More
     * specifically, this method will never return {@code null}. If multiple
     * matching entries are returned by the Directory Server then the request
     * will fail with an {@link MultipleEntriesFoundException}.
     *
     * @param request
     *            The search request.
     * @return The single search result entry returned from the search.
     * @throws ErrorResultException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws UnsupportedOperationException
     *             If this connection does not support search operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If the {@code request} was {@code null}.
     */
    public SearchResultEntry searchSingleEntry(SearchRequest request) throws ErrorResultException {
        return connection.searchSingleEntry(request);
    }

    /**
     * Searches the Directory Server for a single entry using the provided
     * search parameters.
     * <p>
     * If the requested entry is not returned by the Directory Server then the
     * request will fail with an {@link EntryNotFoundException}. More
     * specifically, this method will never return {@code null}. If multiple
     * matching entries are returned by the Directory Server then the request
     * will fail with an {@link MultipleEntriesFoundException}.
     * <p>
     * This method is equivalent to the following code:
     *
     * <pre>
     * SearchRequest request = new SearchRequest(baseObject, scope, filter, attributeDescriptions);
     * connection.searchSingleEntry(request);
     * </pre>
     *
     * @param baseObject
     *            The distinguished name of the base entry relative to which the
     *            search is to be performed.
     * @param scope
     *            The scope of the search.
     * @param filter
     *            The filter that defines the conditions that must be fulfilled
     *            in order for an entry to be returned.
     * @param attributeDescriptions
     *            The names of the attributes to be included with each entry.
     * @return The single search result entry returned from the search.
     * @throws ErrorResultException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws LocalizedIllegalArgumentException
     *             If {@code baseObject} could not be decoded using the default
     *             schema or if {@code filter} is not a valid LDAP string
     *             representation of a filter.
     * @throws UnsupportedOperationException
     *             If this connection does not support search operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If the {@code baseObject}, {@code scope}, or {@code filter}
     *             were {@code null}.
     */
    public SearchResultEntry searchSingleEntry(String baseObject, SearchScope scope, String filter,
            String... attributeDescriptions) throws ErrorResultException {
        return connection.searchSingleEntry(baseObject, scope, filter, attributeDescriptions);
    }

}
