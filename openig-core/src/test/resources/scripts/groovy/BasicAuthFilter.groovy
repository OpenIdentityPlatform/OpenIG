/*
 * Perform basic authentication with a hard-coded user name and password.
 */

def credentials = "bjensen:hifalutin".getBytes().encodeBase64().toString()
exchange.request.headers.add("Authorization", "Basic ${credentials}")

// Credentials are only base64-encoded, not encrypted: Set scheme to HTTPS.

/*
 * When connecting over HTTPS, by default the client tries to trust the server.
 * If the server has no certificate
 * or has a self-signed certificate unknown to the client,
 * then the most likely result is an SSLPeerUnverifiedException.
 *
 * To avoid an SSLPeerUnverifiedException,
 * set up HTTPS correctly on the server.
 * Either use a server certificate signed by a well-known CA,
 * or set up the gateway to trust the server certificate.
 *
 */
// exchange.request.uri cannot be modified, so replace it.
exchange.request.uri = new URI(
        /* scheme */ "https",
        exchange.request.uri.userInfo,
        exchange.request.uri.host,
        exchange.request.uri.port as Integer,
        exchange.request.uri.path,
        exchange.request.uri.query,
        exchange.request.uri.fragment)

// Call the next handler. This returns when the request has been handled.
next.handle(exchange)
