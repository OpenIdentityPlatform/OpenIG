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
 * Copyright 2015-2016 ForgeRock AS.
 */

// OpenAM configuration
var openam_url        = "http://openam.example.com:8088/openam";
var current_realm     = "/";
var access_token_url  = openam_url + "/oauth2/access_token";
var authenticate_url  = openam_url + "/json/authenticate";
var policy_create_url = openam_url + "/json/users/alice/uma/policies?_action=create";
var rpt_url           = openam_url + "/uma/authz_request";

// OpenIG configuration
var openig_url        = "http://api.example.com:8080";
var share_url         = openig_url + "/openig/api/system/objects/router-handler/routes/00-uma/objects/umaservice/share?_action=create";

// UMA RS configuration with OpenAM
var rs_client_id      = "OpenIG";
var rs_client_secret  = "password";
var rs_scope          = "uma_protection";

// Resource owners's OpenAM user
var rs_username       = "alice";
var rs_password       = "password";

// Resources to share
var share             = ".*";

// Protected resource to access
var resource_url = "http://api.example.com:8080/login";

// UMA client configuration with OpenAM
var uma_client_id     = "UmaClient";
var uma_client_secret = "password";
var uma_scope         = "uma_authorization";

// Requesting party's OpenAM user
var uma_username      = "bob";
var uma_password      = "password";
var subjectDn         = "id=bob,ou=user,dc=openam,dc=forgerock,dc=org";

// Current configuration
var conf              = {
                          "OpenAM_conf":
                          {
                            "openam_url": openam_url,
                            "current_realm": current_realm,
                            "access_token_url": access_token_url,
                            "authenticate_url": authenticate_url,
                            "policy_create_url": policy_create_url,
                            "rpt_url": rpt_url
                          },
                          "RS_client_conf":
                          {
                            "rs_client_id": rs_client_id,
                            "rs_client_secret": rs_client_secret,
                            "rs_scope": rs_scope
                          },
                          "UMA_client_conf":
                          {
                            "uma_client_id": uma_client_id,
                            "uma_client_secret": uma_client_secret,
                            "uma_scope": uma_scope
                          },
                          "OpenIG_conf":
                          {
                            "openig_url": openig_url,
                            "share_url": share_url,
                            "share": share
                          },
                          "Alice":
                          {
                            "rs_username": rs_username,
                            "rs_password": rs_password
                          },
                          "Bob":
                          {
                            "uma_username": uma_username,
                            "uma_password": uma_password,
                            "subjectDn": subjectDn
                          }
                        };

/**
 * Return an HTTP basic auth header value.
 */
var authHeader = function (user, pwd) {
    return "Basic " + btoa(user + ':' + pwd);
};

/**
 * Return pre-formatted, pretty-print JSON data for inclusion in HTML.
 */
var json2html = function (data, title) {
    var body = "<pre>" + JSON.stringify(data, undefined, 2) + "</pre>";
    if (title !== undefined) {
        title = "<h3>" + title + "</h3>";
    }
    return title + body;
};
