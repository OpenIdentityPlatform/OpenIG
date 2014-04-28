/*
 * Look up user credentials in a relational database
 * based on the user's email address provided in the request form data,
 * and set the credentials in the exchange headers for the next handler.
 */

def client = new SqlClient()
def credentials = client.getCredentials(exchange.request.form?.mail[0])
exchange.request.headers.add("Username", credentials.Username)
exchange.request.headers.add("Password", credentials.Password)

// The credentials are not protected in the headers, so use HTTPS.
// As exchange.request.uri cannot be modified, replace it instead.
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
