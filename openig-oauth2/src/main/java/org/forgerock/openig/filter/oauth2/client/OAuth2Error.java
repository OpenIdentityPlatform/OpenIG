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

package org.forgerock.openig.filter.oauth2.client;

import static org.forgerock.openig.header.HeaderUtil.parseParameters;
import static org.forgerock.openig.header.HeaderUtil.quote;
import static org.forgerock.openig.header.HeaderUtil.split;
import static org.forgerock.util.Utils.joinAsString;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.fluent.JsonValueException;
import org.forgerock.openig.http.Form;
import org.forgerock.openig.http.FormAttributes;
import org.forgerock.util.Reject;

/**
 * Describes an error which occurred during an OAuth 2.0 authorization request
 * or when performing an authorized request. More specifically, errors are
 * communicated:
 * <ul>
 * <li>as query parameters in a failed authorization call-back. These errors are
 * defined in RFC 6749 # 4.1.2 and comprise of an error code, optional error
 * description, and optional error URI
 * <li>as JSON encoded content in a failed access token request or failed
 * refresh token request. These errors are defined in RFC 6749 # 5.2 and
 * comprise of an error code, optional error description, and optional error URI
 * <li>using the {@code WWW-Authenticate} response header in response to a
 * failed attempt to access an OAuth 2.0 protected resource on a resource
 * server. These errors are defined in RFC 6750 # 3.1 and comprise of an
 * optional error code, optional error description, optional error URI, optional
 * list of required scopes, and optional realm.
 * </ul>
 *
 * @see <a href="http://tools.ietf.org/html/rfc6749#section-4.1.2">RFC 6749 #
 *      4.1.2 - The OAuth 2.0 Authorization Framework</a>
 * @see <a href="http://tools.ietf.org/html/rfc6749#section-5.2">RFC 6749 # 5.2
 *      - The OAuth 2.0 Authorization Framework</a>
 * @see <a href="http://tools.ietf.org/html/rfc6750#section-3.1">RFC 6750 - The
 *      OAuth 2.0 Authorization Framework: Bearer Token Usage</a>
 */
public final class OAuth2Error {
    /**
     * The resource owner or authorization server denied the request.
     *
     * @see <a href="http://tools.ietf.org/html/rfc6749#section-4.1.2">RFC 6749
     *      # 4.1.2 - The OAuth 2.0 Authorization Framework</a>
     */
    public static final String E_ACCESS_DENIED = "access_denied";

    /**
     * The request requires higher privileges than provided by the access token.
     * The resource server SHOULD respond with the HTTP 403 (Forbidden) status
     * code and MAY include the "scope" attribute with the scope necessary to
     * access the protected resource.
     *
     * @see <a href="http://tools.ietf.org/html/rfc6750#section-3.1">RFC 6750 -
     *      The OAuth 2.0 Authorization Framework: Bearer Token Usage</a>
     */
    public static final String E_INSUFFICIENT_SCOPE = "insufficient_scope";

    /**
     * Client authentication failed (e.g., unknown client, no client
     * authentication included, or unsupported authentication method). The
     * authorization server MAY return an HTTP 401 (Unauthorized) status code to
     * indicate which HTTP authentication schemes are supported. If the client
     * attempted to authenticate via the "Authorization" request header field,
     * the authorization server MUST respond with an HTTP 401 (Unauthorized)
     * status code and include the "WWW-Authenticate" response header field
     * matching the authentication scheme used by the client.
     *
     * @see <a href="http://tools.ietf.org/html/rfc6749#section-5.2">RFC 6749 #
     *      5.2 - The OAuth 2.0 Authorization Framework</a>
     */
    public static final String E_INVALID_CLIENT = "invalid_client";

    /**
     * The provided authorization grant (e.g., authorization code, resource
     * owner credentials) or refresh token is invalid, expired, revoked, does
     * not match the redirection URI used in the authorization request, or was
     * issued to another client.
     *
     * @see <a href="http://tools.ietf.org/html/rfc6749#section-5.2">RFC 6749 #
     *      5.2 - The OAuth 2.0 Authorization Framework</a>
     */
    public static final String E_INVALID_GRANT = "invalid_grant";

    /**
     * The request is missing a required parameter, includes an unsupported
     * parameter value (other than grant type), repeats a parameter, includes
     * multiple credentials, utilizes more than one mechanism for authenticating
     * the client, or is otherwise malformed. The resource server SHOULD respond
     * with the HTTP 400 (Bad Request) status code.
     *
     * @see <a href="http://tools.ietf.org/html/rfc6749#section-4.1.2">RFC 6749
     *      # 4.1.2 - The OAuth 2.0 Authorization Framework</a>
     * @see <a href="http://tools.ietf.org/html/rfc6749#section-5.2">RFC 6749 #
     *      5.2 - The OAuth 2.0 Authorization Framework</a>
     * @see <a href="http://tools.ietf.org/html/rfc6750#section-3.1">RFC 6750 -
     *      The OAuth 2.0 Authorization Framework: Bearer Token Usage</a>
     */
    public static final String E_INVALID_REQUEST = "invalid_request";

    /**
     * The requested scope is invalid, unknown, malformed, or exceeds the scope
     * granted by the resource owner.
     *
     * @see <a href="http://tools.ietf.org/html/rfc6749#section-4.1.2">RFC 6749
     *      # 4.1.2 - The OAuth 2.0 Authorization Framework</a>
     * @see <a href="http://tools.ietf.org/html/rfc6749#section-5.2">RFC 6749 #
     *      5.2 - The OAuth 2.0 Authorization Framework</a>
     */
    public static final String E_INVALID_SCOPE = "invalid_scope";

    /**
     * The access token provided is expired, revoked, malformed, or invalid for
     * other reasons. The resource SHOULD respond with the HTTP 401
     * (Unauthorized) status code. The client MAY request a new access token and
     * retry the protected resource request.
     *
     * @see <a href="http://tools.ietf.org/html/rfc6750#section-3.1">RFC 6750 -
     *      The OAuth 2.0 Authorization Framework: Bearer Token Usage</a>
     */
    public static final String E_INVALID_TOKEN = "invalid_token";

    /**
     * The authorization server encountered an unexpected condition that
     * prevented it from fulfilling the request. (This error code is needed
     * because a 500 Internal Server Error HTTP status code cannot be returned
     * to the client via an HTTP redirect.)
     *
     * @see <a href="http://tools.ietf.org/html/rfc6749#section-4.1.2">RFC 6749
     *      # 4.1.2 - The OAuth 2.0 Authorization Framework</a>
     */
    public static final String E_SERVER_ERROR = "server_error";

    /**
     * The authorization server is currently unable to handle the request due to
     * a temporary overloading or maintenance of the server. (This error code is
     * needed because a 503 Service Unavailable HTTP status code cannot be
     * returned to the client via an HTTP redirect.)
     *
     * @see <a href="http://tools.ietf.org/html/rfc6749#section-4.1.2">RFC 6749
     *      # 4.1.2 - The OAuth 2.0 Authorization Framework</a>
     */
    public static final String E_TEMPORARILY_UNAVAILABLE = "temporarily_unavailable";

    /**
     * The authenticated client is not authorized to use this authorization
     * grant type.
     *
     * @see <a href="http://tools.ietf.org/html/rfc6749#section-4.1.2">RFC 6749
     *      # 4.1.2 - The OAuth 2.0 Authorization Framework</a>
     * @see <a href="http://tools.ietf.org/html/rfc6749#section-5.2">RFC 6749 #
     *      5.2 - The OAuth 2.0 Authorization Framework</a>
     */
    public static final String E_UNAUTHORIZED_CLIENT = "unauthorized_client";

    /**
     * The authorization grant type is not supported by the authorization
     * server.
     *
     * @see <a href="http://tools.ietf.org/html/rfc6749#section-5.2">RFC 6749 #
     *      5.2 - The OAuth 2.0 Authorization Framework</a>
     */
    public static final String E_UNSUPPORTED_GRANT_TYPE = "unsupported_grant_type";

    /**
     * The authorization server does not support obtaining an authorization code
     * using this method.
     *
     * @see <a href="http://tools.ietf.org/html/rfc6749#section-4.1.2">RFC 6749
     *      # 4.1.2 - The OAuth 2.0 Authorization Framework</a>
     */
    public static final String E_UNSUPPORTED_RESPONSE_TYPE = "unsupported_response_type";

    /**
     * The name of the field which communicates the error code.
     */
    public static final String F_ERROR = "error";

    /**
     * The name of the field which communicates the error description.
     */
    public static final String F_ERROR_DESCRIPTION = "error_description";

    /**
     * The name of the field which communicates the error uri.
     */
    public static final String F_ERROR_URI = "error_uri";

    /**
     * The name of the field which communicates the realm.
     */
    public static final String F_REALM = "realm";

    /**
     * The name of the field which communicates the scope.
     */
    public static final String F_SCOPE = "scope";

    /**
     * The WWW-Authenticate header prefix, 'Bearer'.
     */
    public static final String H_BEARER = "Bearer";

    /**
     * Singleton instance used for empty WWW-Authenticate headers.
     */
    private static final OAuth2Error EMPTY = new OAuth2Error(null, null, null, null, null);

    /**
     * The WWW-Authenticate header prefix including trailing space separator.
     */
    private static final String H_BEARER_WITH_SPACE = H_BEARER + " ";

    /**
     * Returns an OAuth 2.0 resource server error whose values are determined on
     * a best-effort basis from the provided incomplete error and HTTP status
     * code.
     *
     * @param status
     *            The HTTP status code.
     * @param incomplete
     *            The incomplete and possibly {@code null} error.
     * @return A non-{@code null} error whose error code has been determined
     *         from the HTTP status code.
     */
    public static OAuth2Error bestEffortResourceServerError(final int status,
            final OAuth2Error incomplete) {
        if (incomplete != null && incomplete.error != null) {
            // Seems ok.
            return incomplete;
        }
        final String error;
        switch (status) {
        case 400:
            error = E_INVALID_REQUEST;
            break;
        case 401:
            error = E_INVALID_TOKEN;
            break;
        case 403:
            error = E_INVALID_SCOPE;
            break;
        case 405:
            error = E_INVALID_REQUEST;
            break;
        case 500:
            error = E_SERVER_ERROR;
            break;
        case 503:
            error = E_TEMPORARILY_UNAVAILABLE;
            break;
        default:
            error = E_SERVER_ERROR; // no idea.
            break;
        }
        if (incomplete == null) {
            return new OAuth2Error(null, null, error, null, null);
        } else {
            return new OAuth2Error(incomplete.getRealm(), incomplete.getScope(), error, incomplete
                    .getErrorDescription(), incomplete.getErrorUri());
        }
    }

    /**
     * Returns an OAuth 2.0 error suitable for inclusion in authorization
     * call-back responses and access token and refresh token responses.
     *
     * @param error
     *            The error code specifying the cause of the failure.
     * @param errorDescription
     *            The human-readable ASCII text providing additional
     *            information, or {@code null}.
     * @return The OAuth 2.0 error.
     * @throws NullPointerException
     *             If {@code error} was {@code null}.
     */
    public static OAuth2Error newAuthorizationServerError(final String error,
            final String errorDescription) {
        Reject.ifNull(error);
        return new OAuth2Error(null, null, error, errorDescription, null);
    }

    /**
     * Returns an OAuth 2.0 error suitable for inclusion in authorization
     * call-back responses and access token and refresh token responses.
     *
     * @param error
     *            The error code specifying the cause of the failure.
     * @param errorDescription
     *            The human-readable ASCII text providing additional
     *            information, or {@code null}.
     * @param errorUri
     *            A URI identifying a human-readable web page with information
     *            about the error, or {@code null}.
     * @return The OAuth 2.0 error.
     * @throws NullPointerException
     *             If {@code error} was {@code null}.
     */
    public static OAuth2Error newAuthorizationServerError(final String error,
            final String errorDescription, final String errorUri) {
        Reject.ifNull(error);
        return new OAuth2Error(null, null, error, errorDescription, errorUri);
    }

    /**
     * Returns an OAuth 2.0 error suitable for inclusion in resource server
     * WWW-Authenticate response headers.
     *
     * @param realm
     *            The scope of protection required to access the protected
     *            resource, or {@code null}.
     * @param scope
     *            The required scope(s) of the access token for accessing the
     *            requested resource, or {@code null}.
     * @param error
     *            The error code specifying the cause of the failure, or
     *            {@code null}.
     * @param errorDescription
     *            The human-readable ASCII text providing additional
     *            information, or {@code null}.
     * @param errorUri
     *            A URI identifying a human-readable web page with information
     *            about the error, or {@code null}.
     * @return The OAuth 2.0 error.
     */
    public static OAuth2Error newResourceServerError(final String realm, final List<String> scope,
            final String error, final String errorDescription, final String errorUri) {
        return new OAuth2Error(realm, scope, error, errorDescription, errorUri);
    }

    /**
     * Parses the provided {@link #toString()} representation as an OAuth 2.0
     * error.
     *
     * @param s
     *            The string to parse.
     * @return The parsed OAuth 2.0 error.
     */
    public static OAuth2Error valueOf(final String s) {
        final List<String> attributes = split(s, ',');
        final Map<String, String> map = parseParameters(attributes);
        final String realm = map.get("realm");
        final String scopeString = map.get("scope");
        final List<String> scopes =
                scopeString != null ? Arrays.asList(scopeString.trim().split("\\s+")) : null;
        final String error = map.get("error");
        final String errorDescription = map.get("error_description");
        final String errorUri = map.get("error_uri");
        return new OAuth2Error(realm, scopes, error, errorDescription, errorUri);
    }

    /**
     * Parses the Form representation of an authorization call-back error as an
     * OAuth 2.0 error. Only the error, error description, and error URI fields
     * will be included.
     *
     * @param form
     *            The Form representation of an authorization call-back error.
     * @return The parsed OAuth 2.0 error.
     */
    public static OAuth2Error valueOfForm(final Form form) {
        return new OAuth2Error(null, null, form.getFirst(F_ERROR), form
                .getFirst(F_ERROR_DESCRIPTION), form.getFirst(F_ERROR_URI));
    }

    /**
     * Parses the Form representation of an authorization call-back error as an
     * OAuth 2.0 error. Only the error, error description, and error URI fields
     * will be included.
     *
     * @param form
     *            The Form representation of an authorization call-back error.
     * @return The parsed OAuth 2.0 error.
     */
    public static OAuth2Error valueOfForm(final FormAttributes form) {
        return new OAuth2Error(null, null, form.getFirst(F_ERROR), form
                .getFirst(F_ERROR_DESCRIPTION), form.getFirst(F_ERROR_URI));
    }

    /**
     * Parses the JSON representation of an access token error response as an
     * OAuth 2.0 error. Only the error, error description, and error URI fields
     * will be included.
     *
     * @param json
     *            The JSON representation of an access token error response.
     * @return The parsed OAuth 2.0 error.
     * @throws IllegalArgumentException
     *             If the JSON content was malformed.
     */
    public static OAuth2Error valueOfJsonContent(final Map<String, Object> json) {
        final JsonValue jv = new JsonValue(json);
        try {
            return new OAuth2Error(null, null, jv.get(F_ERROR).asString(), jv.get(
                    F_ERROR_DESCRIPTION).asString(), jv.get(F_ERROR_URI).asString());
        } catch (final JsonValueException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Parses the provided WWW-Authenticate header content as an OAuth 2.0
     * error.
     *
     * @param s
     *            The string containing the WWW-Authenticate header content.
     * @return The parsed OAuth 2.0 error.
     * @throws IllegalArgumentException
     *             If the header value was malformed.
     */
    public static OAuth2Error valueOfWWWAuthenticateHeader(final String s) {
        if (s.equals(H_BEARER)) {
            return EMPTY;
        } else if (s.startsWith(H_BEARER_WITH_SPACE)) {
            return valueOf(s.substring(H_BEARER_WITH_SPACE.length()));
        } else {
            throw new IllegalArgumentException("Malformed WWW-Authenticate header '" + s + "'");
        }
    }

    private final String error;
    private final String errorDescription;
    private final String errorUri;
    private final String realm;
    private final List<String> scope;
    private transient String stringValue;

    private OAuth2Error(final String realm, final List<String> scope, final String error,
            final String errorDescription, final String errorUri) {
        this.realm = realm;
        this.scope =
                scope != null ? Collections.unmodifiableList(scope) : Collections
                        .<String> emptyList();
        this.error = error;
        this.errorDescription = errorDescription;
        this.errorUri = errorUri;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        } else if (obj instanceof OAuth2Error) {
            return toString().equals(((OAuth2Error) obj).toString());
        } else {
            return false;
        }
    }

    /**
     * Returns the error code specifying the cause of the failure.
     *
     * @return The error code specifying the cause of the failure, or
     *         {@code null} if no error code was provided (which may be the case
     *         for WWW-Authenticate headers).
     */
    public String getError() {
        return error;
    }

    /**
     * Returns the human-readable ASCII text providing additional information,
     * used to assist the client developer in understanding the error that
     * occurred.
     *
     * @return The human-readable ASCII text providing additional information,
     *         or {@code null} if no description was provided.
     */
    public String getErrorDescription() {
        return errorDescription;
    }

    /**
     * Returns a URI identifying a human-readable web page with information
     * about the error, used to provide the client developer with additional
     * information about the error.
     *
     * @return A URI identifying a human-readable web page with information
     *         about the error, or {@code null} if no error URI was provided.
     */
    public String getErrorUri() {
        return errorUri;
    }

    /**
     * Returns the scope of protection required to access the protected
     * resource. The realm is only included with {@code WWW-Authenticate}
     * headers in response to a failure to access a protected resource.
     *
     * @return The scope of protection required to access the protected
     *         resource, or {@code null} if no realm was provided (which will
     *         always be the case for authorization call-back failures and
     *         access/refresh token requests).
     */
    public String getRealm() {
        return realm;
    }

    /**
     * Returns the required scope of the access token for accessing the
     * requested resource. The scope is only included with
     * {@code WWW-Authenticate} headers in response to a failure to access a
     * protected resource.
     *
     * @return The required scope of the access token for accessing the
     *         requested resource, which may be empty (never {@code null}) if no
     *         scope was provided (which will always be the case for
     *         authorization call-back failures and access/refresh token
     *         requests).
     */
    public List<String> getScope() {
        return scope;
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    /**
     * Returns {@code true} if this error includes an error code and it matches
     * the provided error code.
     *
     * @param error
     *            The error code.
     * @return {@code true} if this error includes an error code and it matches
     *         the provided error code.
     */
    public boolean is(final String error) {
        return error.equalsIgnoreCase(this.error);
    }

    /**
     * Returns the form representation of this error suitable for inclusion in
     * an authorization call-back query. Only the error, error description, and
     * error URI fields will be included.
     *
     * @return The form representation of this error suitable for inclusion in
     *         an authorization call-back query.
     */
    public Form toForm() {
        final Form form = new Form();
        if (error != null) {
            form.add(F_ERROR, error);
        }
        if (errorDescription != null) {
            form.add(F_ERROR_DESCRIPTION, errorDescription);
        }
        if (errorUri != null) {
            form.add(F_ERROR_URI, errorUri);
        }
        return form;
    }

    /**
     * Returns the JSON representation of this error formatted as an access
     * token error response. Only the error, error description, and error URI
     * fields will be included.
     *
     * @return The JSON representation of this error formatted as an access
     *         token error response.
     */
    public Map<String, Object> toJsonContent() {
        final Map<String, Object> json = new LinkedHashMap<String, Object>(3);
        if (error != null) {
            json.put(F_ERROR, error);
        }
        if (errorDescription != null) {
            json.put(F_ERROR_DESCRIPTION, errorDescription);
        }
        if (errorUri != null) {
            json.put(F_ERROR_URI, errorUri);
        }
        return json;
    }

    @Override
    public String toString() {
        // Use lazy initialization: minor race conditions don't matter.
        if (stringValue == null) {
            final StringBuilder builder = new StringBuilder();
            appendAttribute(builder, F_REALM, realm);
            appendAttribute(builder, F_SCOPE, scope.isEmpty() ? null : joinAsString(" ", scope));
            appendAttribute(builder, F_ERROR, error);
            appendAttribute(builder, F_ERROR_DESCRIPTION, errorDescription);
            appendAttribute(builder, F_ERROR_URI, errorUri);
            stringValue = builder.toString();
        }
        return stringValue;
    }

    /**
     * Returns the string representation of this error formatted as a
     * {@code WWW-Authenticate} header.
     *
     * @return The string representation of this error formatted as a
     *         {@code WWW-Authenticate} header.
     */
    public String toWWWAuthenticateHeader() {
        final String stringValue = toString();
        return stringValue.isEmpty() ? H_BEARER : H_BEARER_WITH_SPACE + stringValue;
    }

    private void addSeparator(final StringBuilder builder) {
        if (builder.length() > 0) {
            builder.append(", ");
        }
    }

    private void appendAttribute(final StringBuilder builder, final String key, final String value) {
        if (value != null) {
            addSeparator(builder);
            builder.append(key).append('=').append(quote(value));
        }
    }
}
