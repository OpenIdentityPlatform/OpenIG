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
 * Copyright 2014-2016 ForgeRock AS.
 */

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
