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
 * Portions Copyrighted 2011-2014 ForgeRock AS.
 */

package org.forgerock.openig.handler.saml;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.sun.identity.plugin.session.SessionException;
import com.sun.identity.saml2.assertion.Assertion;
import com.sun.identity.saml2.assertion.AuthnStatement;
import com.sun.identity.saml2.assertion.Subject;
import com.sun.identity.saml2.common.SAML2Constants;
import com.sun.identity.saml2.common.SAML2Exception;
import com.sun.identity.saml2.common.SAML2Utils;
import com.sun.identity.saml2.jaxb.entityconfig.SPSSOConfigElement;
import com.sun.identity.saml2.logging.LogUtil;
import com.sun.identity.saml2.meta.SAML2MetaManager;
import com.sun.identity.saml2.profile.LogoutUtil;
import com.sun.identity.saml2.profile.SPACSUtils;
import com.sun.identity.saml2.profile.SPSSOFederate;
import com.sun.identity.saml2.profile.SPSingleLogout;
import com.sun.identity.saml2.servlet.SPSingleLogoutServiceSOAP;

/**
 * Receives HTTP requests from the Dispatcher for all federation end points. Requests are then diverted to the correct
 * end point processing module. Processing modules are for single sign-on and single logout.
 */
class FederationServlet extends HttpServlet {

    /** Default Realm is always / in this case. */
    private static final String DEFAULT_REALM = "/";

    private static final long serialVersionUID = 1L;

    /** The attribute mapping. */
    private Map<String, String> attributeMapping = new HashMap<String, String>();

    /** The value contained in the assertion subject is set as the value of the attribute subjectName in the session. */
    private String subjectMapping;

    /** The delimiter to use when there are multiple contexts in the assertion. */
    private String authnContextDelimiter;

    /** The name to use when placing context values into the session. */
    private String authnContext;

    private String sessionIndexMapping;

    private String redirectURI;

    private String logoutURI;

    private String assertionConsumerEndpoint;

    private String sPinitiatedSSOEndpoint;

    private String singleLogoutEndpoint;

    /** IDP Single Logout SOAP Endpoint. */
    private String singleLogoutEndpointSoap;

    /** SP Single Logout Endpoint. */
    private String sPinitiatedSLOEndpoint;

    /**
     * Constructs a federation servlet according to the specified parameters.
     *
     * @param attributeMapping
     *            The attribute mapping.
     * @param subjectMapping
     *            The value contained in the assertion subject is set as the value of the attribute subjectName in the
     *            session.
     * @param authnContextDelimiter
     *            The delimiter to use when there are multiple contexts in the assertion.
     * @param authnContext
     *            The name to use when placing context values into the session.
     * @param sessionIndexMapping
     *            The IDP's sessionIndex for the user is sent in the assertion.
     * @param redirectURI
     *            The redirectURI should be set to the page the Form-Filter recognizes as the login page for the target
     *            application.
     * @param logoutURI
     *            The logoutURI should be set to the URI which logs the user out of the target application.
     * @param assertionConsumerEndpoint
     *            The assertionMapping defines how to transform the attributes from the incoming assertion to attribute
     *            value pairs in the session.
     * @param sPinitiatedSSOEndpoint
     *            The default value is SPInitiatedSSO.
     * @param singleLogoutEndpoint
     *            The default value of fedletSLO is the same as the Fedlet.
     * @param singleLogoutEndpointSoap
     *            IDP Single Logout SOAP Endpoint.
     * @param sPinitiatedSLOEndpoint
     *            SP Single Logout Endpoint.
     */
    FederationServlet(Map<String, String> attributeMapping, String subjectMapping, String authnContextDelimiter,
            String authnContext, String sessionIndexMapping, String redirectURI, String logoutURI,
            String assertionConsumerEndpoint, String sPinitiatedSSOEndpoint, String singleLogoutEndpoint,
            String singleLogoutEndpointSoap, String sPinitiatedSLOEndpoint) {
        super();
        this.attributeMapping = Collections.unmodifiableMap(attributeMapping);
        this.subjectMapping = subjectMapping;
        this.authnContextDelimiter = authnContextDelimiter;
        this.authnContext = authnContext;
        this.sessionIndexMapping = sessionIndexMapping;
        this.redirectURI = redirectURI;
        this.logoutURI = logoutURI;
        this.assertionConsumerEndpoint = assertionConsumerEndpoint;
        this.sPinitiatedSSOEndpoint = sPinitiatedSSOEndpoint;
        this.singleLogoutEndpoint = singleLogoutEndpoint;
        this.singleLogoutEndpointSoap = singleLogoutEndpointSoap;
        this.sPinitiatedSLOEndpoint = sPinitiatedSLOEndpoint;
    }

    @Override
    public void service(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        try {
            String path = request.getPathInfo();
            if (path.indexOf(assertionConsumerEndpoint) > 0) {
                serviceAssertionConsumer(request, response);
            } else if (path.indexOf(sPinitiatedSSOEndpoint) > 0) {
                serviceSPInitiatedSSO(request, response);
            } else if (path.indexOf(sPinitiatedSLOEndpoint) > 0) {
                serviceSPInitiatedSLO(request, response);
            } else if (path.indexOf(singleLogoutEndpointSoap) > 0) {
                serviceIDPInitiatedSLOSOAP(request, response);
            } else if (path.indexOf(singleLogoutEndpoint) > 0) {
                serviceIDPInitiatedSLO(request, response);
            } else {
                System.out.println("FederationServlet warning: URI not in service " + path);
            }
        } catch (SAML2Exception sme) {
            errorResponse(response, sme.getMessage());
        } catch (SessionException se) {
            errorResponse(response, se.getMessage());
        }
    }

    private static void errorResponse(HttpServletResponse response, String message) throws IOException {
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "SSO Failed:" + message);
    }

    /**
     * Whether IDP or SP initiated, the final request ends up here. The assertion is validated, attributes are retrieved
     * from and set in the HttpSession where downstream filters can access them and pass them on to the target
     * application.
     */
    @SuppressWarnings("unchecked")
    private void serviceAssertionConsumer(HttpServletRequest request, HttpServletResponse response) throws IOException,
            ServletException, SAML2Exception, SessionException {
        Map map = SPACSUtils.processResponseForFedlet(request, response);
        String relayURI = (String) map.get(SAML2Constants.RELAY_STATE);
        if (relayURI != null && !"".equals(relayURI)) {
            redirectURI = relayURI;
        }
        addAttributesToSession(request, map);
        /*
         * Redirect back to the original target application's login page and let the filters take over. If the relayURI
         * is set in the assertion we must use that, otherwise we will use the configured value, which should be the
         * login page for the target application.
         */
        response.sendRedirect(redirectURI);
    }

    /**
     * Store attribute value pairs in the session based on the assertionMapping found in config.json. The intent is to
     * have a filter use one of these attributes as the subject and possibly the password. The presence of these
     * attributes in the Session implies the assertion has been processed and validated. Format of the attributeMapping:
     * sessionAttributename: assertionAttribute. sessionAttributeName: attribute name added to the session.
     * assertionAttribute: Name of the attribute to fetch from the assertion, the value becomes the value in the
     * session.
     *
     * @param request
     *            The servlet request.
     * @param assertion
     *            The assertion mapping found in the configuration file(.json).
     */
    private void addAttributesToSession(HttpServletRequest request, Map assertion) {
        HttpSession httpSession = request.getSession();
        Map attributeStatement = (Map) assertion.get(SAML2Constants.ATTRIBUTE_MAP);
        if (attributeStatement != null) {
            if (LogUtil.isAccessLoggable(Level.INFO)) {
                System.out.println("FederationServlet attribute statement: " + attributeStatement);
            }
            for (String key : attributeMapping.keySet()) {
                HashSet t = (HashSet) attributeStatement.get(attributeMapping.get(key));
                if (t != null) {
                    String sessionValue = (String) t.iterator().next();
                    httpSession.setAttribute(key, sessionValue);
                    if (LogUtil.isAccessLoggable(Level.INFO)) {
                        System.out.println("FederationServlet adding to session: " + key + " = " + sessionValue);
                    }
                } else {
                    System.out.println("FederationServlet: Warning no assertion attribute found for:"
                            + attributeMapping.get(key));
                }
            }
        } else {
            if (LogUtil.isAccessLoggable(Level.INFO)) {
                System.out.println("FederationServlet attribute statement was not present in assertion");
            }
        }
        if (subjectMapping != null) {
            String subjectValue = ((Subject) assertion.get(SAML2Constants.SUBJECT)).getNameID().getValue();
            httpSession.setAttribute(subjectMapping, subjectValue);
            if (LogUtil.isAccessLoggable(Level.INFO)) {
                System.out.println("FederationServlet adding subject to session: " + subjectMapping + " = "
                        + subjectValue);
            }
        }

        if (sessionIndexMapping != null) {
            String sessionIndexValue = (String) assertion.get(SAML2Constants.SESSION_INDEX);
            httpSession.setAttribute(sessionIndexMapping, sessionIndexValue);
            if (LogUtil.isAccessLoggable(Level.INFO)) {
                System.out.println("FederationServlet adding session index: " + sessionIndexMapping + " = "
                        + sessionIndexValue);
            }
        }

        if (authnContext != null) {
            @SuppressWarnings("unchecked")
            List<AuthnStatement> authnStatements = ((Assertion) assertion.get(SAML2Constants.ASSERTION))
                    .getAuthnStatements();
            StringBuilder authnContextValues = new StringBuilder();
            for (AuthnStatement authnStatement : authnStatements) {
                String authnContextValue = authnStatement.getAuthnContext().getAuthnContextClassRef();
                if (authnContextValue != null && !authnContextValue.isEmpty()) {
                    authnContextValues.append(authnContextValue);
                    authnContextValues.append(authnContextDelimiter);
                }
            }
            if (authnContextValues.length() > 0) {
                // remove the last delimiter as it is redundant
                authnContextValues.deleteCharAt(authnContextValues.length() - 1);
                httpSession.setAttribute(authnContext, authnContextValues.toString());
                if (LogUtil.isAccessLoggable(Level.INFO)) {
                    System.out.println("FederationServlet adding authentication contexts to session: " + authnContext
                            + " = " + authnContextValues);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void serviceSPInitiatedSSO(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException, SAML2Exception, SessionException {
        String metaAlias = request.getParameter(SAML2Constants.METAALIAS);
        if (metaAlias == null || metaAlias.length() == 0) {
            SAML2MetaManager manager = new SAML2MetaManager();
            List spMetaAliases = manager.getAllHostedServiceProviderMetaAliases(DEFAULT_REALM);
            if (spMetaAliases != null && !spMetaAliases.isEmpty()) {
                metaAlias = (String) spMetaAliases.get(0);
            }
        }
        String idpEntityID = request.getParameter(SAML2Constants.IDPENTITYID);
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
        if (idpEntityID == null || idpEntityID.length() == 0) {
            SAML2MetaManager manager = new SAML2MetaManager();
            List idpEntities = manager.getAllRemoteIdentityProviderEntities(DEFAULT_REALM);
            if (idpEntities != null && !idpEntities.isEmpty()) {
                idpEntityID = (String) idpEntities.get(0);
            }
        }
        if (metaAlias == null || idpEntityID == null) {
            throw new SAML2Exception("No metadata for SP or IDP");
        }
        SPSSOFederate.initiateAuthnRequest(request, response, metaAlias, idpEntityID, paramsMap);
    }

    /**
     * This implementation is based on the spSingleLogoutInit.jsp from OpenAM Expects to find the NameID and
     * SessionIndex in the session, these are stored during the IDP login process. Optional request parameters are :
     * "RelayState" - the target URL on successful Single Logout "spEntityID" - SP entity ID. When it is missing, first
     * SP from metadata is used. "idpEntityID" - IDP entity ID. When it is missing, first IDP from metadata is used.
     * "binding" - binding used for this request and when not set it will use the default binding of the IDP
     */
    @SuppressWarnings("unchecked")
    private void serviceSPInitiatedSLO(HttpServletRequest request, HttpServletResponse response) throws IOException,
            ServletException, SAML2Exception, SessionException {

        if (LogUtil.isAccessLoggable(Level.INFO)) {
            System.out.println("FederationServlet.serviceSPInitiatedSLO entering");
        }

        HttpSession httpSession = request.getSession();
        // Retrieve these values from the session, if they do not exist then the session has expired
        // or this is being called before we have authenticated to the IDP
        String nameID = (String) httpSession.getAttribute(subjectMapping);
        String sessionIndex = (String) httpSession.getAttribute(sessionIndexMapping);
        if (nameID == null || nameID.isEmpty() || sessionIndex == null || sessionIndex.isEmpty()) {
            throw new SAML2Exception(SAML2Utils.bundle.getString("nullNameID"));
        }

        SAML2MetaManager manager = new SAML2MetaManager();
        String relayState = request.getParameter(SAML2Constants.RELAY_STATE);
        String spEntityID = request.getParameter(SAML2Constants.SPENTITYID);
        String binding = request.getParameter(SAML2Constants.BINDING);
        String idpEntityID = request.getParameter(SAML2Constants.IDPENTITYID);

        // If the idpEntityID has not been specified then read it from the IDP metadata.
        if (idpEntityID == null || idpEntityID.isEmpty()) {
            List<String> idpEntities = manager.getAllRemoteIdentityProviderEntities(DEFAULT_REALM);
            if (idpEntities != null && !idpEntities.isEmpty()) {
                // Just take the first one since only one is supported
                idpEntityID = idpEntities.get(0);
            }
        }
        if (LogUtil.isAccessLoggable(Level.INFO)) {
            System.out.println("FederationServlet.serviceSPInitiatedSLO idpEntityID: " + idpEntityID);
        }

        String metaAlias = null;
        // If the spEntityID has not been specified then read it from the SP metadata.
        if (spEntityID == null || spEntityID.isEmpty()) {
            List<String> spMetaAliases = manager.getAllHostedServiceProviderMetaAliases(DEFAULT_REALM);
            if (spMetaAliases != null && !spMetaAliases.isEmpty()) {
                // Just take the first one since only one is supported
                metaAlias = spMetaAliases.get(0);
                spEntityID = manager.getEntityByMetaAlias(metaAlias);
            }
        } else {
            SPSSOConfigElement spConfig = manager.getSPSSOConfig(DEFAULT_REALM, spEntityID);
            if (spConfig != null) {
                metaAlias = spConfig.getMetaAlias();
            }
        }
        if (LogUtil.isAccessLoggable(Level.INFO)) {
            System.out.println("FederationServlet.serviceSPInitiatedSLO metaAlias: " + metaAlias);
            System.out.println("FederationServlet.serviceSPInitiatedSLO spEntityID: " + spEntityID);
        }

        if (metaAlias == null || idpEntityID == null) {
            throw new SAML2Exception("No metadata for SP or IDP");
        }

        // If the binding has not been specified then look up the IDP's default binding.
        if (binding == null) {
            binding = LogoutUtil.getSLOBindingInfo(request, metaAlias, SAML2Constants.SP_ROLE, idpEntityID);
        }

        if (!SAML2Utils.isSPProfileBindingSupported(DEFAULT_REALM, spEntityID, SAML2Constants.SLO_SERVICE, binding)) {
            if (LogUtil.isAccessLoggable(Level.SEVERE)) {
                System.out.println("FederationServlet.serviceSPInitiatedSLO unsupported binding: " + binding);
            }
            throw new SAML2Exception(SAML2Utils.bundle.getString("unsupportedBinding"));
        }

        if (LogUtil.isAccessLoggable(Level.INFO)) {
            System.out.println("FederationServlet.serviceSPInitiatedSLO binding: " + binding);
        }

        HashMap<String, String> paramsMap = new HashMap<String, String>(7);
        paramsMap.put(SAML2Constants.INFO_KEY, spEntityID + "|" + idpEntityID + "|" + nameID);
        paramsMap.put(SAML2Constants.SESSION_INDEX, sessionIndex);
        paramsMap.put(SAML2Constants.METAALIAS, metaAlias);
        paramsMap.put(SAML2Constants.IDPENTITYID, idpEntityID);
        paramsMap.put(SAML2Constants.ROLE, SAML2Constants.SP_ROLE);
        paramsMap.put(SAML2Constants.BINDING, binding);

        // If the relayState has not been specified try to use the SP default otherwise set it to the logoutURI if set.
        if (relayState == null || relayState.isEmpty()) {
            relayState = SAML2Utils.getAttributeValueFromSSOConfig(DEFAULT_REALM, spEntityID, SAML2Constants.SP_ROLE,
                    SAML2Constants.DEFAULT_RELAY_STATE);
            if (relayState == null || (relayState.isEmpty() && logoutURI != null && !logoutURI.isEmpty())) {
                relayState = logoutURI;
            }
        }
        if (relayState != null && !relayState.isEmpty()) {
            paramsMap.put(SAML2Constants.RELAY_STATE, relayState);
        }

        if (LogUtil.isAccessLoggable(Level.INFO)) {
            System.out.println("FederationServlet.serviceSPInitiatedSLO relayState: " + relayState);
        }

        SPSingleLogout.initiateLogoutRequest(request, response, binding, paramsMap);

        if (SAML2Constants.SOAP.equalsIgnoreCase(binding)) {
            response.sendRedirect(relayState);
        }
    }

    private void serviceIDPInitiatedSLO(HttpServletRequest request, HttpServletResponse response) throws IOException,
            ServletException, SAML2Exception, SessionException {

        if (LogUtil.isAccessLoggable(Level.INFO)) {
            System.out.println("FederationServlet.serviceIDPInitiatedSLO entering");
        }

        String relayState = request.getParameter(SAML2Constants.RELAY_STATE);
        if (relayState == null || (relayState.isEmpty() && logoutURI != null && !logoutURI.isEmpty())) {
            relayState = logoutURI;
        }
        if (LogUtil.isAccessLoggable(Level.INFO)) {
            System.out.println("FederationServlet.serviceIDPInitiatedSLO relayState: " + relayState);
        }
        // Check if this is a request as part of an IDP initiated SLO
        String samlRequest = request.getParameter(SAML2Constants.SAML_REQUEST);
        if (samlRequest != null) {
            if (LogUtil.isAccessLoggable(Level.INFO)) {
                System.out.println("FederationServlet.serviceIDPInitiatedSLO processing request");
            }
            SPSingleLogout.processLogoutRequest(request, response, samlRequest, relayState);
        } else {
            // Otherwise it might be a response from the IDP as part of a SP initiated SLO
            String samlResponse = request.getParameter(SAML2Constants.SAML_RESPONSE);
            if (samlResponse != null) {
                if (LogUtil.isAccessLoggable(Level.INFO)) {
                    System.out.println("FederationServlet.serviceIDPInitiatedSLO processing response");
                }
                SPSingleLogout.processLogoutResponse(request, response, samlResponse, relayState);
            }
        }
    }

    private static void serviceIDPInitiatedSLOSOAP(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {

        if (LogUtil.isAccessLoggable(Level.INFO)) {
            System.out.println("FederationServlet.serviceIDPInitiatedSLOSOAP entering");
        }

        SPSingleLogoutServiceSOAP spSingleLogoutServiceSOAP = new SPSingleLogoutServiceSOAP();
        spSingleLogoutServiceSOAP.doPost(request, response);
    }
}
