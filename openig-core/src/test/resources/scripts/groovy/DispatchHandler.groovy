import org.forgerock.openig.http.Response

/*
 * This simplistic dispatcher matches the path part of the HTTP request.
 * If the path is /login, it checks Username and Password headers,
 * accepting bjensen:hifalutin, and returning HTTP 403 Forbidden to others.
 * Otherwise it returns HTTP 401 Unauthorized.
 */

// Rather than get the response from an external source,
// this handler produces the response itself.
exchange.response = new Response();

switch (exchange.request.uri.path) {

    case "/login":

        if (exchange.request.headers.Username[0] == "bjensen" &&
                exchange.request.headers.Password[0] == "hifalutin") {

            exchange.response.status = 200
            exchange.response.entity = "<html><p>Welcome back, Babs!</p></html>"

        } else {

            exchange.response.status = 403
            exchange.response.entity = "<html><p>Authorization required</p></html>"

        }

        break

    default:

        exchange.response.status = 401
        exchange.response.entity = "<html><p>Please <a href='./login'>log in</a>.</p></html>"

        break

}
