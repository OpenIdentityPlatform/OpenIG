import org.forgerock.http.protocol.Response
import org.forgerock.http.protocol.Status

/*
 * This simplistic dispatcher matches the path part of the HTTP request.
 * If the path is /login, it checks Username and Password headers,
 * accepting bjensen:hifalutin, and returning HTTP 403 Forbidden to others.
 * Otherwise it returns HTTP 401 Unauthorized.
 */

// Rather than return a Promise of a response from an external source,
// this script returns the response itself.
response = new Response();

switch (request.uri.path) {

    case "/login":

        if (request.headers.Username.values[0] == "bjensen" &&
                request.headers.Password.values[0] == "hifalutin") {

            response.status = Status.OK
            response.entity = "<html><p>Welcome back, Babs!</p></html>"

        } else {

            response.status = Status.FORBIDDEN
            response.entity = "<html><p>Authorization required</p></html>"

        }

        break

    default:

        response.status = Status.UNAUTHORIZED
        response.entity = "<html><p>Please <a href='./mylogin'>log in</a>.</p></html>"

        break

}

// Return the locally created response, no need to wrap it into a Promise
return response

