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
 * information: "Portions Copyrighted [year] [name of copyright owner]".
 *
 * Copyright © 2010–2011 ApexIdentity Inc. All rights reserved.
 */

package org.forgerock.openig.pbworks;

// Java Standard Edition
import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.URI;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.lang.Object;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

// JSON Fluent
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.fluent.JsonValueException;

// OpenIG Core
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.HeapUtil;
import org.forgerock.openig.heap.NestedHeaplet;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.log.LogLevel;
import org.forgerock.openig.log.LogTimer;
import org.forgerock.openig.util.JsonValueUtil;
import org.forgerock.openig.util.URIUtil;

import org.forgerock.openig.el.ExpressionException;
import org.forgerock.openig.handler.GenericHandler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.DispatchHandler.Binding;
import org.forgerock.openig.http.Form;
import org.forgerock.openig.http.Request;
import org.forgerock.openig.http.Response;
import org.forgerock.openig.io.Streamer;
import org.forgerock.openig.log.LogTimer;
import org.forgerock.openig.util.CaseInsensitiveMap;
import org.forgerock.openig.util.CaseInsensitiveSet;
import org.forgerock.openig.http.HttpUtil;
import org.forgerock.openig.util.MultiValueMap;
import org.forgerock.openig.util.URIUtil;
import org.forgerock.openig.filter.Chain;
import org.forgerock.openig.http.Message;
import org.forgerock.openig.resource.ResourceException;
import org.json.simple.parser.JSONParser;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;


/**
 * PBWorks Authentication Filter
 *
 * @author Jamie Nelson
 */
public class PBWorksLoginHandler extends GenericHandler {

    /** URI paths for the PBWork's APIs */
    private static final String LOGIN_PATH = "/api_v2/op/GetSessionUrl";
    private static final String SET_PATH = "/api_v2/op/SetUserProfileValue";
    private static final String ADD_USER_TO_WORKSPACE_PATH = "/api_v2/op/AddUsersToWorkspace";
    
    public static class Permission {
        /** Value to fetch from the user. */ 
        public Expression expr;
        /** Permission to set if there is a match */ 
        public String perm;
    }

    /** A permission for PBWorks users must be set at login time. These mappings 
     * allow us to set the user permission based on attribute values in the user
     * profile or exchange.  Ift here are no mappings we use the defaultPermission.
     */
    public final List<Permission> permissionMappings = new ArrayList<Permission>();
    
    public static class WorkSpace {
        /** Value to fetch from the user. */ 
        public Expression expr;
        /** Name of the work space */ 
        public String name;
        /** Permission to set if there is a match */ 
        public String perm;
    }

    /** When a user is dynamically created at PBWorks we may want to assign them
     * to 1 or more workspaces.  Each workspace allows you to assign permissions
     * of {admin,edit,write,read,page,deny} for the user. These mappings allow
     * us to assign users to workspaces based on attribute values found in their
     * profile or the exchange.  If there are no mappings we do not add the users
     * to any workspace.
     */
    public final List<WorkSpace> workSpaceMappings = new ArrayList<WorkSpace>();
    
    public Handler handler; 
    
    /** Handler to be called when a user does not have access to PBWorks */
    public Handler accessDeniedHandler;

    /** Secret sent with every login request */
    public String sharedSecret;
    
    /** Secret sent with provisioning requests, it is the secret of a 
     * user with the admin role at PBWorks 
     */
    public String adminSecret;
    
    /** the dns name of the company hosting the delegated auth server  */
    public String domain;
    
    /** workspace name, usually the name of the company for example, apexidentity  */
    public String networkName;
 
    /** Expression used to fetch the userName from the exchange */
    public Expression userNameExp;

    /**
     * Key/value pairs with the key as a user profile attribute name at PBWorks
     * and the value an Expression resolved from the exchange, an attribute 
     * provider or a literal value. The key/value pairs are used to set values in
     * the user profile at PBWorks.  This operation takes place after a user has
     * been dynamically created and needs additional profile attributes.
     */
    public final CaseInsensitiveMap<Expression> newUserAttrs = new CaseInsensitiveMap<Expression>();   
    
    /** 
     * Length of time in seconds the session is valid, will be added 
     * to the current time in the request and sent as part of the login
     */
    public long sessionLength;
    
    /** PBworks AuthN and provisioning Server URI */
    public String PBWorksAuthURI;
    
    /** Method to use for request to PBWorks */
    public String requestMethod;
   
    /**
     * Creates a new PBWorks handler.
     */
    public PBWorksLoginHandler() {
    }

    /**
     * Called to handle the PBWorks login. This handler assumes the user has already
     * authenticated to the master appliction and the userName is set in the session
     * or the credentials are hardcoded for testing.
     */
    @Override
    public void handle(Exchange exchange) throws HandlerException, IOException {
        LogTimer timer = logger.getTimer().start();
        logger.debug("Reqest URI: " + exchange.request.uri.toString());
        String validUntil = Long.toString((System.currentTimeMillis() / 1000L) + sessionLength);
        Form form = new Form();
        form.fromRequestQuery(exchange.request);
        String returnTo = form.getFirst("return_to");
        
        try {
            String userName = userNameExp.eval(exchange, String.class);
            String userPermission = getUserPermission(exchange, userName);
            if (userPermission == null) {
            	accessDeniedHandler.handle(exchange);
            	return;
            }
            sendRequest(exchange, PBWorksAuthURI + LOGIN_PATH + "/sso_key/" + sharedSecret +
                                           "/email/" + URLEncoder.encode(userName, "UTF-8") +
                                           "/perm/" + userPermission + "/valid_until/" + validUntil +
                                           "/return_to/" + URLEncoder.encode(returnTo, "UTF-8"));
            
            JsonValue jv = parseResponse(exchange.response);
            String finalRedirect = jv.get("url").required().asString();
            Boolean userCreated = jv.get("created").required().asBoolean();
            String uid = jv.get("uid").required().asString();
            
            // If created is true then PBWorks dynamically created the user profile so we need to
            // set the users first_name, last_name, title, and other attributes in the newUserAttrs
            // config Map
            logger.info("Authenticated: Username: " + userName + " Permission: " + userPermission);
            if (userCreated) {
                setNewUserAttributes(exchange, uid);
                addUserToWorkspace(exchange, uid);
            }
      
            // After successful login and/or setting user attributes we must redirect to the url sent in
            // the response after the login request.  This request creates the user session
            exchange.response = new Response();
            exchange.response.status = 302;
            exchange.response.headers.add("Location", finalRedirect);
            logger.debug("Final redirect to: " + finalRedirect);
        }
        catch (JsonValueException je) {
            throw new HandlerException("PBWorksLoginHandler: JsonValueException: " + je.getMessage());
        }
        timer.stop();
    }
    
    
    private void sendRequest(Exchange exchange, String targetURI) throws HandlerException, IOException {
        try {
            exchange.request.uri = new URI(targetURI);
            logger.debug("Sent Request: " + targetURI);
            exchange.request.method = requestMethod;
            exchange.request.headers.putSingle("Host",exchange.request.uri.getHost());
            handler.handle(exchange);
        }
        catch (URISyntaxException ex) {
            throw new HandlerException("PBWorksLoginHandler: URISyntaxException:" + ex.getMessage());
        }
    }
    
    /*
     * Parse the response from PBWorks API call. Responses are in JSON wrapped in a secure wrapper so
     * we need to strip off the wrapper before calling the JSON parser. 
     */
    private JsonValue parseResponse(Response response) throws JsonValueException, IOException, HandlerException {
        
        CharArrayWriter caw = new CharArrayWriter();
        Streamer.stream(HttpUtil.entityReader(response, true, null), caw);
        String content =  caw.toString();
        logger.debug("Response: " + content);
        int begin = content.indexOf("{");
        int end = content.lastIndexOf("}");
        if (begin == -1 || end == -1) {
            throw new HandlerException("PBWorksLoginHandler: error parsing response");   
        }
        String jsonContent = content.substring(begin, end + 1);
        JsonValue jv = new JsonValue(JSONValue.parse(jsonContent));
        String errorString = jv.get("error_string").asString();
        int errorStatus = jv.get("error_status").defaultTo(0).asInteger();
        if (errorStatus != 0 || errorString != null) {
            logger.error("Resonse error: " + content);
            throw new HandlerException("PBWorksLoginHandler: error parsing response");
        }
        if (response.entity != null) {
            response.entity.close();
        }
        return jv;
    }
    
    /*
     *  Updates the the newly created users profile with the key value pairs found in the
     *  newUserAttrs list. 
     */
    private void setNewUserAttributes(Exchange exchange, String uid) throws HandlerException, 
                                                                 IOException, JsonValueException {
        for (String key : this.newUserAttrs.keySet()) {
            Expression ex = newUserAttrs.get(key);
            String value = ex.eval(exchange, String.class);
            if (value == null) {
                logger.warning("setNewUserAttributes: null value for: " + key);
                continue;
            }
            sendRequest(exchange, PBWorksAuthURI + SET_PATH + "/user_key/" + adminSecret +
                                            "/uid/" + uid + "/key/" + URLEncoder.encode(key, "UTF-8") +
                                            "/value/" + URLEncoder.encode(value, "UTF-8"));
                
            parseResponse(exchange.response);
        }
    }
    
    /*
     * If the value returned from the expression matches one of the strings found in the
     * matches list then add the user to the workspace.
     */
    private void addUserToWorkspace(Exchange exchange, String uid) throws HandlerException, 
                                                                 IOException, JsonValueException {
        for (WorkSpace w : workSpaceMappings) {
            Boolean eval = (Boolean)w.expr.eval(exchange);
            if (eval != null && eval) {
                logger.debug("addUserToWorkspace(): " + uid + ":" + w.name + ":" + w.perm);
                sendRequest(exchange, "https://" + networkName + "-" + w.name + ".pbworks.com" + 
                        ADD_USER_TO_WORKSPACE_PATH + "/user_key/" + adminSecret +
                        "/perm/" + w.perm + "/uids/" + uid + "/invite/false");
                parseResponse(exchange.response);
            }
        }
    }
    
    /**
     * Permission mappings maps a user role to a permission at PBWorks.  If no mapping is
     * set or no mapping is found an exception is thrown since users must be explicitly
     * permitted to login.
     */
    private String getUserPermission(Exchange exchange, String userName) throws HandlerException, IOException {
        for (Permission p : permissionMappings) {
            Boolean eval = (Boolean)p.expr.eval(exchange);
            if (eval != null && eval) {
                return p.perm;
            }
        }
        logger.warning("No Permission Mapping: Access Denied user: " + userName);
        return null;
    }

    public static class Heaplet extends NestedHeaplet {
        @Override public Object create() throws HeapException, JsonValueException {
            PBWorksLoginHandler lh = new PBWorksLoginHandler();
            lh.sharedSecret = config.get("sharedSecret").required().asString();
            lh.adminSecret = config.get("adminSecret").required().asString();
            lh.domain = config.get("domain").required().asString();
            lh.userNameExp = JsonValueUtil.asExpression(config.get("userNameExp").required());            
            lh.sessionLength = config.get("sessionLength").defaultTo("86400").asLong();
            lh.PBWorksAuthURI = config.get("PBWorksAuthURI").required().asString();
            lh.networkName = config.get("networkName").required().asString();
            lh.handler = HeapUtil.getRequiredObject(heap, config.get("handler"), Handler.class);
            lh.accessDeniedHandler = HeapUtil.getRequiredObject(heap, config.get("accessDeniedHandler"), Handler.class);
            lh.requestMethod = config.get("requestMethod").defaultTo("GET").asString();
            
            JsonValue add = config.get("newUserAttrs").defaultTo(Collections.emptyMap()).expect(Map.class);
            for (String key : add.keys()) {
                String value = add.get(key).required().asString();
                lh.newUserAttrs.put(key, JsonValueUtil.asExpression(add.get(key).required()));                
            }
            for (JsonValue jv : config.get("permissionMappings").required().expect(List.class)) {
                jv.required().expect(Map.class);
                Permission p = new Permission();
                p.expr = JsonValueUtil.asExpression(jv.get("expr"));
                p.perm = jv.get("perm").asString();
                lh.permissionMappings.add(p);
            }
            for (JsonValue jv : config.get("workSpaceMappings").required().expect(List.class)) {
                jv.required().expect(Map.class);
                WorkSpace w = new WorkSpace();
                w.expr = JsonValueUtil.asExpression(jv.get("expr"));
                w.name = jv.get("name").asString();
                w.perm = jv.get("perm").asString();
                lh.workSpaceMappings.add(w);
            }
            return lh;
        }
    }
}
