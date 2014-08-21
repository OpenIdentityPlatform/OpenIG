import org.forgerock.openig.http.Response
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

exchange.request.headers.add("title", title)

exchange.response = new Response()
exchange.response.status = "${status}" as Integer

exchange.response.reason = reason[2]
assert names.size() == 4
exchange.response.entity = "<html><p>Coffee ==> " + names + "</p></html>"
