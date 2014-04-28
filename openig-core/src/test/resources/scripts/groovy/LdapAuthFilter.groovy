import org.forgerock.opendj.ldap.*
import org.forgerock.openig.http.Response
import org.forgerock.openig.io.ByteArrayBranchingStream

/*
 * Perform LDAP authentication based on user credentials from a form.
 *
 * If LDAP authentication succeeds, then call the next handler.
 * If there is a failure, send a response back to the user.
 */

username = exchange.request.form?.username[0]
password = exchange.request.form?.password[0]

// For testing purposes, the LDAP host and port are provided in the exchange.
// Edit as needed to match your directory service.
host = exchange.ldapHost ?: "localhost"
port = exchange.ldapPort ?: 1389

client = ldap.connect(host, port as Integer)
try {

    // Assume the username is an exact match of either
    // the user ID, the email address, or the user's full name.
    filter = "(|(uid=%s)(mail=%s)(cn=%s))"

    user = client.searchSingleEntry(
            "ou=people,dc=example,dc=com",
            ldap.scope.sub,
            ldap.filter(filter, username, username, username))

    client.bind(user.name as String, password?.toCharArray())

    // Authentication succeeded.

    // Set a header (or whatever else you want to do here).
    exchange.request.headers.add("Ldap-User-Dn", user.name)

    // Call the next handler. This returns when the request has been handled.
    next.handle(exchange)

} catch (AuthenticationException e) {

    // LDAP authentication failed, so fail the exchange with
    // HTTP status code 403 Forbidden.

    exchange.response = new Response()
    exchange.response.status = 403
    exchange.response.reason = e.message
    exchange.response.entity = new ByteArrayBranchingStream(
            ("<html><p>Authentication failed: " + e.message + "</p></html>")
                    .getBytes())

} catch (Exception e) {

    // Something other than authentication failed on the server side,
    // so fail the exchange with HTTP 500 Internal Server Error.

    exchange.response = new Response()
    exchange.response.status = 500
    exchange.response.reason = e.message
    exchange.response.entity = new ByteArrayBranchingStream(
            ("<html><p>Server error: " + e.message + "</p></html>")
                    .getBytes())

} finally {
    client.close()
}
