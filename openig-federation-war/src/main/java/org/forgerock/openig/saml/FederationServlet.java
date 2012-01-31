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
 * Portions Copyrighted 2011 ForgeRock AS.
 */

package org.forgerock.openig.saml;

// Java Standard Edition
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Level;

// Java Enterprise Edition
import javax.servlet.Filter;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

// JSON Fluent
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.fluent.JsonValueException;

// OpenIG Core
import org.forgerock.openig.config.ConfigUtil;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.filter.HeaderFilter;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.HeapUtil;
import org.forgerock.openig.heap.NestedHeaplet;
import org.forgerock.openig.servlet.DispatchServlet;
import org.forgerock.openig.servlet.GenericServletHeaplet;
import org.forgerock.openig.servlet.HandlerServlet;
import org.forgerock.openig.util.CaseInsensitiveMap;
import org.forgerock.openig.util.CaseInsensitiveSet;

// OpenAM
import com.sun.identity.federation.common.FSUtils;
import com.sun.identity.plugin.session.SessionManager;
import com.sun.identity.plugin.session.SessionProvider;
import com.sun.identity.plugin.session.SessionException;
import com.sun.identity.saml2.assertion.Subject;
import com.sun.identity.saml.common.SAMLUtils;
import com.sun.identity.saml2.common.SAML2Constants;
import com.sun.identity.saml2.common.SAML2Exception;
import com.sun.identity.saml2.common.SAML2Utils;
import com.sun.identity.saml2.logging.LogUtil;
import com.sun.identity.saml2.meta.SAML2MetaException;
import com.sun.identity.saml2.meta.SAML2MetaManager;
import com.sun.identity.saml2.meta.SAML2MetaUtils;
import com.sun.identity.saml2.profile.IDPProxyUtil;
import com.sun.identity.saml2.profile.ResponseInfo; 
import com.sun.identity.saml2.profile.SPACSUtils;
import com.sun.identity.saml2.profile.SPCache;
import com.sun.identity.saml2.profile.SPSingleLogout;
import com.sun.identity.saml2.profile.SPSSOFederate;
import com.sun.identity.saml2.protocol.Response;
import com.sun.identity.shared.encode.URLEncDec;

/**
 * Receives HTTP requests from the Dispatcher for all federation end points.
 * Requests are then diverted to the correct end point processing module.
 * Processing modules are for single sign-on and single logout.
 *
 * @author Jamie F. Nelson
 */
public class FederationServlet extends HttpServlet {

    /** TODO: Description. */
    private static final long serialVersionUID = 1L;

    /** TODO: Description. */
    final Map<String,String> attributeMapping = new HashMap<String,String>();

    /** TODO: Description. */
    private String subjectMapping;

    /** TODO: Description. */
    private String sessionIndexMapping;

    /** TODO: Description. */
    private String redirectURI;

    /** TODO: Description. */
    private String logoutURI;

    /** TODO: Description. */
    private String assertionConsumerEndpoint;

    /** TODO: Description. */
    private String SPinitiatedSSOEndpoint;

    /** TODO: Description. */
    private String singleLogoutEndpoint;
    
    @Override
    public void service(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        try {
            String path = request.getPathInfo();
            if (path.indexOf(assertionConsumerEndpoint) > 0) {
                serviceAssertionConsumer(request, response);
            } else if (path.indexOf(SPinitiatedSSOEndpoint) > 0) {
                serviceSPInitiatedSSO(request, response);
            } else if (path.indexOf(singleLogoutEndpoint) > 0) {
                serviceIDPInitiatedSLO(request, response);
            } else {
                System.out.println("FederationServlet warning: URI not in service");
            }
        } catch (SAML2Exception sme) {
            errorResponse(response, sme.getMessage());
        } catch (SessionException se) {
            errorResponse(response, se.getMessage());
        }
    }

    /**
     * TODO: Description.
     *
     * @param response TODO.
     * @param message TODO.
     * @throws IOException TODO.
     */
    private void errorResponse(HttpServletResponse response, String message) throws IOException {
        response.sendError(response.SC_INTERNAL_SERVER_ERROR, "SSO Failed:" +  message);
    }
    
    /*
     * Whether IDP or SP initiated, the final request ends up here.  The assertion is
     * validated, attributes are retrieved from and set in the HttpSession where
     * downstream filters can access them and pass them on to the target application.
     */
    @SuppressWarnings("unchecked")
    private void serviceAssertionConsumer(HttpServletRequest request, HttpServletResponse response)
    throws IOException, ServletException, SAML2Exception, SessionException {
        Map map = SPACSUtils.processResponseForFedlet(request, response);
        String relayURI = (String) map.get(SAML2Constants.RELAY_STATE);
        if (relayURI != null & !relayURI.equals("")) {
            redirectURI = relayURI;
        }        
        addAttributesToSession(request, map);
        /*
         * Redirect back to the original target application's login page and let the filters
         * take over. If the relayURI is set in the assertion we must use that, otherwise we
         * will use the configured value, which should be the login page for the target
         * application.
         */
        response.sendRedirect(redirectURI);
    }
    
    /** 
     * Store attribute value pairs in the session based on the assertionMapping  found in
     * config.json. The intent is to have a filter use one of these attributes as the subject
     * and possibly the password. The presence of these attributes in the Session implies the
     * assertion has been processed and validated. Format of the attributeMapping:
     *
     * sessionAttributename: assertionAttribute.
     * sessionAttributeName: attribute name added to the session.
     * assertionAttribute: Name of the attribute to fetch from the assertion, the value becomes
     * the value in the session.
     * 
     * @param request TODO.
     * @param assertion TODO.
      */
    private void addAttributesToSession(HttpServletRequest request, Map assertion) {
        HttpSession httpSession = request.getSession();
        String sessionValue = null;
        Map attributeStatement = (Map)assertion.get(SAML2Constants.ATTRIBUTE_MAP);
        if (LogUtil.isAccessLoggable(Level.INFO)) {
            System.out.println("FederationServlet attribute statement: " + attributeStatement);
        }
        for (String key : attributeMapping.keySet()) {
            HashSet t = (HashSet)attributeStatement.get(attributeMapping.get(key));
            if (t != null) {
                sessionValue = (String)t.iterator().next();
                httpSession.setAttribute(key, sessionValue);
                if (LogUtil.isAccessLoggable(Level.INFO)) {
                    System.out.println("FederationServlet adding to session: " + key + " = " + sessionValue);
                }
            } else { 
                 System.out.println("FederationServlet: Warning no assertion attribute found for:" + attributeMapping.get(key));
                 continue;
            }
        }
        if (subjectMapping != null) {
            String subjectValue = ((Subject)assertion.get(SAML2Constants.SUBJECT)).getNameID().getValue();
            httpSession.setAttribute(subjectMapping, subjectValue);
            if (LogUtil.isAccessLoggable(Level.INFO)) {
                System.out.println("FederationServlet adding subject to session: " + subjectMapping + " = " + subjectValue);
            }
        }

        if (sessionIndexMapping != null) {
           String sessionIndexValue = (String)assertion.get(SAML2Constants.SESSION_INDEX);
           httpSession.setAttribute(sessionIndexMapping, sessionIndexValue);
           if (LogUtil.isAccessLoggable(Level.INFO)) {
               System.out.println("FederationServlet adding session index: " + sessionIndexMapping + " = " + sessionIndexValue);
           }
        }
    }
    
    @SuppressWarnings("unchecked")
    private void serviceSPInitiatedSSO (HttpServletRequest request, HttpServletResponse response)
    throws IOException, ServletException, SAML2Exception, SessionException {
        String metaAlias = request.getParameter("metaAlias");
        if ((metaAlias ==  null) || (metaAlias.length() == 0)) {
            SAML2MetaManager manager = new SAML2MetaManager();
            List spMetaAliases = manager.getAllHostedServiceProviderMetaAliases("/");
            if ((spMetaAliases != null) && !spMetaAliases.isEmpty()) {
                metaAlias = (String) spMetaAliases.get(0);
            }
        }
        String idpEntityID = request.getParameter("idpEntityID");
        Map paramsMap = SAML2Utils.getParamsMap(request);
        List list = new ArrayList();
        list.add(SAML2Constants.NAMEID_TRANSIENT_FORMAT);

        // next line testing to see if we can change the name format
        paramsMap.put(SAML2Constants.NAMEID_POLICY_FORMAT, list);

// TODO: add option to specify artifact 
        if (paramsMap.get(SAML2Constants.BINDING) == null) {
            // use POST binding
            list = new ArrayList();
            list.add(SAML2Constants.HTTP_POST);
            paramsMap.put(SAML2Constants.BINDING, list);
        }
        if ((idpEntityID == null) || (idpEntityID.length() == 0)) {
            SAML2MetaManager manager = new SAML2MetaManager();
            List idpEntities = manager.getAllRemoteIdentityProviderEntities("/");
            if ((idpEntities != null) && !idpEntities.isEmpty()) {
                idpEntityID = (String)idpEntities.get(0);
            }
        }
        if (metaAlias == null || idpEntityID == null) {
            throw new SAML2Exception("No metadata for SP or IDP");
        }
        SPSSOFederate.initiateAuthnRequest(request, response, metaAlias, idpEntityID, paramsMap);
    }
       
    private void serviceIDPInitiatedSLO (HttpServletRequest request, HttpServletResponse response)
    throws IOException, ServletException, SAML2Exception, SessionException {
        String relayState = request.getParameter(SAML2Constants.RELAY_STATE);
        String samlRequest = request.getParameter(SAML2Constants.SAML_REQUEST);
        SPSingleLogout.processLogoutRequest(request, response, samlRequest, relayState);
        response.sendRedirect(logoutURI);
    }

    public static class Heaplet extends NestedHeaplet {
        @Override public Object create() throws HeapException, JsonValueException {
            final Map<String,String> tagSwapMap = new HashMap<String,String>();
            FederationServlet servlet = new FederationServlet();
            JsonValue mappings = config.get("assertionMapping").required().expect(Map.class);
            for (String key : mappings.keys()) {
                servlet.attributeMapping.put(key, mappings.get(key).asString());
            }
            servlet.subjectMapping = config.get("subjectMapping").asString();
            servlet.sessionIndexMapping = config.get("sessionIndexMapping").asString();
            servlet.redirectURI = config.get("redirectURI").asString();
            servlet.logoutURI = config.get("logoutURI").asString();
            servlet.assertionConsumerEndpoint = config.get("assertionConsumerEndpoint").defaultTo("fedletapplication").asString();
            servlet.SPinitiatedSSOEndpoint = config.get("SPinitiatedSSOEndpoint").defaultTo("SPInitiatedSSO").asString();
            servlet.singleLogoutEndpoint = config.get("singleLogoutEndpoint").defaultTo("fedletSlo").asString();
            /*
             * Get the gateway configuration directory and set it as a system property to
             * override the default openFed location. Federation config files will reside in
             * the SAML directory.
             */
            String openFedConfigDir = ConfigUtil.getDirectory("ForgeRock", "SAML").getPath();
            System.out.println("FederationServlet init: " + openFedConfigDir);
            Properties p = System.getProperties();
            p.setProperty("com.sun.identity.fedlet.home", openFedConfigDir);
            System.setProperties(p);
            return servlet;
        }
    }
}
