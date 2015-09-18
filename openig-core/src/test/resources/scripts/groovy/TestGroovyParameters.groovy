import org.forgerock.http.protocol.Response
import org.forgerock.http.protocol.Status
/*
 * The parameters are stored directly in bindings. 
 * e.g. The json configuration looks like :
 * { 
 *     "name": "myGroovyFilter", 
 *     "type": "ScriptableFilter", 
 *     "config": {
 *         "type": "application/x-groovy",
 *         "file": "myScriptName.groovy",
 *         "args": { 
 *             "title": "Coffee time",
 *             "status": 418,
 *             "reason": [ 
 *                 "Not Acceptable", 
 *                 "I'm a teapot",
 *                 "Acceptable" ],
 *             "names": {
 *                 "1": "koffie",
 *                 "2": "kafe",
 *                 "3": "cafe",
 *                 "4": "kafo"
 *             }
 *         } 
 *     }
 * }
 */

request.headers.add("title", title)

response = new Response(Status.valueOf("${status}" as Integer))

assert names.size() == 4
response.entity = "<html><p>Coffee ==> " + names + "</p></html>"

// Return the locally created response, no need to wrap it into a Promise
return response
