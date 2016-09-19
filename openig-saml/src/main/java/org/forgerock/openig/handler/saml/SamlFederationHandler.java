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
package org.forgerock.openig.handler.saml;

import static java.lang.String.format;
import static org.forgerock.openig.heap.Keys.ENVIRONMENT_HEAP_KEY;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.forgerock.http.Handler;
import org.forgerock.http.header.LocationHeader;
import org.forgerock.http.protocol.Form;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.http.session.Session;
import org.forgerock.http.session.SessionContext;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.config.Environment;
import org.forgerock.openig.heap.GenericHeapObject;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.identity.common.ShutdownManager;
import com.sun.identity.plugin.session.SessionException;
import com.sun.identity.saml2.assertion.Assertion;
import com.sun.identity.saml2.assertion.AuthnStatement;
import com.sun.identity.saml2.assertion.Subject;
import com.sun.identity.saml2.common.SAML2Constants;
import com.sun.identity.saml2.common.SAML2Exception;
import com.sun.identity.saml2.common.SAML2Utils;
import com.sun.identity.saml2.jaxb.entityconfig.SPSSOConfigElement;
import com.sun.identity.saml2.meta.SAML2MetaManager;
import com.sun.identity.saml2.profile.CacheObject;
import com.sun.identity.saml2.profile.LogoutUtil;
import com.sun.identity.saml2.profile.SPACSUtils;
import com.sun.identity.saml2.profile.SPCache;
import com.sun.identity.saml2.profile.SPSSOFederate;
import com.sun.identity.saml2.profile.SPSingleLogout;
import com.sun.identity.saml2.servlet.SPSingleLogoutServiceSOAP;

/**
 * The SAML federation handler.
 */
public class SamlFederationHandler extends GenericHeapObject implements Handler {

    private static final Logger logger = LoggerFactory.getLogger(SamlFederationHandler.class);

    /** Default Realm is always / in this case. */
    private static final String DEFAULT_REALM = "/";

    /**
     * Marker for already-completed CHF Response (response filled through Servlet API).
     */
    private static final Response RESPONSE_ALREADY_COMPLETED = null;

    /** The attribute mapping. */
    private final Map<String, String> attributeMapping;

    /** The value contained in the assertion subject is set as the value of the attribute subjectName in the session. */
    private final String subjectMapping;

    /** The delimiter to use when there are multiple contexts in the assertion. */
    private final String authnContextDelimiter;

    /** The name to use when placing context values into the session. */
    private final String authnContext;

    private final String sessionIndexMapping;

    private final String redirectURI;

    private final String logoutURI;

    private final String assertionConsumerEndpoint;

    private final String sPinitiatedSSOEndpoint;

    private final String singleLogoutEndpoint;

    /** IDP Single Logout SOAP Endpoint. */
    private final String singleLogoutEndpointSoap;

    /** SP Single Logout Endpoint. */
    private final String sPinitiatedSLOEndpoint;

    /**
     * Constructs a federation handler according to the specified parameters.
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
    SamlFederationHandler(Map<String, String> attributeMapping,
                          String subjectMapping,
                          String authnContextDelimiter,
                          String authnContext,
                          String sessionIndexMapping,
                          String redirectURI,
                          String logoutURI,
                          String assertionConsumerEndpoint,
                          String sPinitiatedSSOEndpoint,
                          String singleLogoutEndpoint,
                          String singleLogoutEndpointSoap,
                          String sPinitiatedSLOEndpoint) {
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
    public Promise<Response, NeverThrowsException> handle(final Context context, final Request request) {
        HttpServletRequest servletRequest = adaptRequest(context, request);
        HttpServletResponse servletResponse = adaptResponse(context);

        Session session = context.asContext(SessionContext.class).getSession();
        try {
            String path = request.getUri().getPath();
            if (path.indexOf(assertionConsumerEndpoint) > 0) {
                return complete(serviceAssertionConsumer(session, servletRequest, servletResponse));
            } else if (path.indexOf(sPinitiatedSSOEndpoint) > 0) {
                return complete(serviceSPInitiatedSSO(request, servletRequest, servletResponse));
            } else if (path.indexOf(sPinitiatedSLOEndpoint) > 0) {
                return complete(serviceSPInitiatedSLO(request, session, servletRequest, servletResponse));
            } else if (path.indexOf(singleLogoutEndpointSoap) > 0) {
                return complete(serviceIDPInitiatedSLOSOAP(servletRequest, servletResponse));
            } else if (path.indexOf(singleLogoutEndpoint) > 0) {
                return complete(serviceIDPInitiatedSLO(request, session, servletRequest, servletResponse));
            } else {
                logger.warn("handle: URI not in service {}", request.getUri());
                return complete(RESPONSE_ALREADY_COMPLETED);
            }
        } catch (IOException ioe) {
            return complete(withError(servletResponse, ioe.getMessage()));
        } catch (ServletException se) {
            return complete(withError(servletResponse, se.getMessage()));
        } catch (SAML2Exception sme) {
            return complete(withError(servletResponse, sme.getMessage()));
        } catch (SessionException se) {
            return complete(withError(servletResponse, se.getMessage()));
        }
    }

    private static Promise<Response, NeverThrowsException> complete(Response response) {
        return Promises.newResultPromise(response);
    }

    /**
     * Whether IDP or SP initiated, the final request ends up here.
     * <p>
     * The assertion is validated, attributes are retrieved from and set in the HttpSession where downstream filters
     * can access them and pass them on to the target application.
     */
    private Response serviceAssertionConsumer(Session session,
                                              HttpServletRequest request,
                                              HttpServletResponse response) throws IOException,
                                                                                   ServletException,
                                                                                   SAML2Exception,
                                                                                   SessionException {
        Map<?, ?> map = SPACSUtils.processResponseForFedlet(request, response, response.getWriter());
        addAttributesToSession(session, map);
        /*
         * Redirect back to the original target application's login page and let the filters take over. If the relayURI
         * is set in the assertion we must use that, otherwise we will use the configured value, which should be the
         * login page for the target application.
         */
        String relayURI = (String) map.get(SAML2Constants.RELAY_STATE);
        String uri = isRelayURIProvided(relayURI) ? relayURI : redirectURI;
        return sendRedirect(uri);
    }

    private boolean isRelayURIProvided(String relayURI) {
        return relayURI != null && !relayURI.isEmpty();
    }

    /**
     * Store attribute value pairs in the session based on the assertionMapping found in config file.
     * <p>
     * The intent is to have a filter use one of these attributes as the subject and possibly the password. The
     * presence of these attributes in the Session implies the assertion has been processed and validated.
     *
     * @param session
     *            Request's {@link Session}
     * @param assertion
     *            SAML assertion content
     */
    private void addAttributesToSession(final Session session, Map<?, ?> assertion) {
        @SuppressWarnings("unchecked")
        Map<String, Set<String>> attributeStatement =
                (Map<String, Set<String>>) assertion.get(SAML2Constants.ATTRIBUTE_MAP);
        if (attributeStatement != null && !attributeStatement.isEmpty()) {
            for (String key : attributeMapping.keySet()) {
                Set<String> values = attributeStatement.get(attributeMapping.get(key));
                if (values != null && !values.isEmpty()) {
                    // Add the attribute values as an Array of strings.
                    session.put(key, new ArrayList<>(values));
                } else {
                    logger.warn("addAttributesToSession: Warning no assertion attribute found for : {}",
                                attributeMapping.get(key));
                }
            }
        } else {
            logger.warn("addAttributesToSession: Attribute statement was not present in assertion");
        }
        if (subjectMapping != null) {
            String subjectValue = ((Subject) assertion.get(SAML2Constants.SUBJECT)).getNameID().getValue();
            session.put(subjectMapping, subjectValue);
            logger.debug("addAttributesToSession: Adding subject to session: {} = {}", subjectMapping, subjectValue);
        }

        if (sessionIndexMapping != null) {
            String sessionIndexValue = (String) assertion.get(SAML2Constants.SESSION_INDEX);
            session.put(sessionIndexMapping, sessionIndexValue);
            logger.debug("addAttributesToSession: Adding session index: {} = {}",
                         sessionIndexMapping,
                         sessionIndexValue);
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
                session.put(authnContext, authnContextValues.toString());
                logger.debug("addAttributesToSession: Adding authentication contexts to session: {} = {}",
                             authnContext,
                             authnContextValues);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Response serviceSPInitiatedSSO(Request request,
                                              HttpServletRequest servletRequest,
                                              HttpServletResponse servletResponse) throws SAML2Exception {
        Form form = request.getForm();
        String metaAlias = form.getFirst(SAML2Constants.METAALIAS);
        if (metaAlias == null || metaAlias.length() == 0) {
            SAML2MetaManager manager = new SAML2MetaManager();
            List<String> spMetaAliases =
                    manager.getAllHostedServiceProviderMetaAliases(DEFAULT_REALM);
            if (spMetaAliases != null && !spMetaAliases.isEmpty()) {
                metaAlias = spMetaAliases.get(0);
            }
        }
        String idpEntityID = form.getFirst(SAML2Constants.IDPENTITYID);
        Map<String, List<String>> paramsMap = SAML2Utils.getParamsMap(servletRequest);
        List<String> list = new ArrayList<>();
        list.add(SAML2Constants.NAMEID_TRANSIENT_FORMAT);

        // next line testing to see if we can change the name format
        paramsMap.put(SAML2Constants.NAMEID_POLICY_FORMAT, list);

        // TODO: add option to specify artifact
        if (paramsMap.get(SAML2Constants.BINDING) == null) {
            // use POST binding
            list = new ArrayList<>();
            list.add(SAML2Constants.HTTP_POST);
            paramsMap.put(SAML2Constants.BINDING, list);
        }
        if (idpEntityID == null || idpEntityID.length() == 0) {
            SAML2MetaManager manager = new SAML2MetaManager();
            List<String> idpEntities = manager.getAllRemoteIdentityProviderEntities(DEFAULT_REALM);
            if (idpEntities != null && !idpEntities.isEmpty()) {
                idpEntityID = idpEntities.get(0);
            }
        }
        if (metaAlias == null || idpEntityID == null) {
            throw new SAML2Exception("No metadata for SP or IDP");
        }
        SPSSOFederate.initiateAuthnRequest(servletRequest, servletResponse, metaAlias, idpEntityID, paramsMap, null);
        return RESPONSE_ALREADY_COMPLETED;
    }

    /**
     * This implementation is based on the {@literal spSingleLogoutInit.jsp} from OpenAM.
     * Expects to find the {@literal NameID} and {@literal SessionIndex} in the session, these are stored during the
     * IDP login process.
     * <p>
     * Optional request parameters are:
     * <ul>
     *     <li>{@literal RelayState} - the target URL on successful Single Logout.</li>
     *     <li>{@literal spEntityID} - SP entity ID. When it is missing, first SP from metadata is used.</li>
     *     <li>{@literal idpEntityID} - IDP entity ID. When it is missing, first IDP from metadata is used.</li>
     *     <li>{@literal binding} - binding used for this request and when not set it will use the default binding of
     *     the IDP.</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private Response serviceSPInitiatedSLO(Request request, Session session,
                                           HttpServletRequest servletRequest,
                                           HttpServletResponse servletResponse)
            throws IOException, SAML2Exception {
        logger.debug("serviceSPInitiatedSLO entering");

        // Retrieve these values from the session, if they do not exist then the session has expired
        // or this is being called before we have authenticated to the IDP
        String nameID = (String) session.get(subjectMapping);
        String sessionIndex = (String) session.get(sessionIndexMapping);
        if (nameID == null || nameID.isEmpty() || sessionIndex == null || sessionIndex.isEmpty()) {
            throw new SAML2Exception(SAML2Utils.bundle.getString("nullNameID"));
        }

        Form form = request.getForm();
        SAML2MetaManager manager = new SAML2MetaManager();
        String relayState = form.getFirst(SAML2Constants.RELAY_STATE);
        String spEntityID = form.getFirst(SAML2Constants.SPENTITYID);
        String binding = form.getFirst(SAML2Constants.BINDING);
        String idpEntityID = form.getFirst(SAML2Constants.IDPENTITYID);

        // If the idpEntityID has not been specified then read it from the IDP metadata.
        if (idpEntityID == null || idpEntityID.isEmpty()) {
            List<String> idpEntities = manager.getAllRemoteIdentityProviderEntities(DEFAULT_REALM);
            if (idpEntities != null && !idpEntities.isEmpty()) {
                // Just take the first one since only one is supported
                idpEntityID = idpEntities.get(0);
            }
        }
        logger.debug("serviceSPInitiatedSLO idpEntityID: {}", idpEntityID);


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
        logger.debug("serviceSPInitiatedSLO metaAlias: {}", metaAlias);
        logger.debug("serviceSPInitiatedSLO spEntityID: {}", spEntityID);


        if (metaAlias == null || idpEntityID == null) {
            throw new SAML2Exception("No metadata for SP or IDP");
        }

        // If the binding has not been specified then look up the IDP's default binding.
        if (binding == null) {
            binding = LogoutUtil.getSLOBindingInfo(servletRequest, metaAlias, SAML2Constants.SP_ROLE, idpEntityID);
        }

        if (!SAML2Utils.isSPProfileBindingSupported(DEFAULT_REALM, spEntityID, SAML2Constants.SLO_SERVICE, binding)) {
            logger.error("serviceSPInitiatedSLO unsupported binding: {}", binding);
            throw new SAML2Exception(SAML2Utils.bundle.getString("unsupportedBinding"));
        }

        logger.debug("serviceSPInitiatedSLO binding: {}", binding);

        Map<String, String> paramsMap = new HashMap<>(7);
        paramsMap.put(SAML2Constants.INFO_KEY, spEntityID + "|" + idpEntityID + "|" + nameID);
        paramsMap.put(SAML2Constants.SESSION_INDEX, sessionIndex);
        paramsMap.put(SAML2Constants.SP_METAALIAS, metaAlias);
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

        logger.debug("serviceSPInitiatedSLO relayState: {}", relayState);

        SPSingleLogout.initiateLogoutRequest(servletRequest,
                                             servletResponse,
                                             servletResponse.getWriter(),
                                             binding,
                                             paramsMap,
                                             null);

        if (SAML2Constants.SOAP.equalsIgnoreCase(binding)) {
            return sendRedirect(relayState);
        }

        return RESPONSE_ALREADY_COMPLETED;
    }

    private Response serviceIDPInitiatedSLO(final Request request,
                                            final Session session,
                                            HttpServletRequest servletRequest,
                                            HttpServletResponse servletResponse)
            throws SAML2Exception, SessionException, IOException {
        logger.debug("serviceIDPInitiatedSLO entering");

        String relayState = getLogoutRelayState(request);
        logger.debug("serviceIDPInitiatedSLO relayState : {}", relayState);

        Form form = request.getForm();
        // Check if this is a request as part of an IDP initiated SLO
        String samlRequest = form.getFirst(SAML2Constants.SAML_REQUEST);
        Response response = RESPONSE_ALREADY_COMPLETED;
        if (samlRequest != null) {
            logger.debug("serviceIDPInitiatedSLO processing IDP request");
            SPSingleLogout.processLogoutRequest(servletRequest,
                                                servletResponse,
                                                servletResponse.getWriter(),
                                                samlRequest,
                                                relayState);
        } else {
            // Otherwise it might be a response from the IDP as part of a SP initiated SLO
            String samlResponse = form.getFirst(SAML2Constants.SAML_RESPONSE);
            if (samlResponse != null) {
                logger.debug("serviceIDPInitiatedSLO processing IDP response");
                SPSingleLogout.processLogoutResponse(servletRequest, servletResponse, samlResponse, relayState);
                if (relayState != null) {
                    response = sendRedirect(relayState);
                }
            }
        }

        cleanSession(session);
        return response;
    }

    private Response serviceIDPInitiatedSLOSOAP(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {

        logger.debug("serviceIDPInitiatedSLOSOAP entering");

        SPSingleLogoutServiceSOAP spSingleLogoutServiceSOAP = new SPSingleLogoutServiceSOAP();
        spSingleLogoutServiceSOAP.doPost(request, response);

        return RESPONSE_ALREADY_COMPLETED;
    }

    private String getLogoutRelayState(Request request) {

        Form form = request.getForm();
        String relayState = form.getFirst(SAML2Constants.RELAY_STATE);
        if (relayState != null) {
            // Check the SP cache for the actual relayState value as the relayState is
            // often passed as an ID to the IDP as part of the SP initiated SLO. Based on
            // code from spSingleLogoutPOST.jsp in OpenAM
            CacheObject tmpRs = (CacheObject) SPCache.relayStateHash.remove(relayState);
            if (tmpRs != null) {
                relayState = (String) tmpRs.getObject();
            }
        }

        if (relayState == null || (relayState.isEmpty() && logoutURI != null && !logoutURI.isEmpty())) {
            relayState = logoutURI;
        }

        return relayState;
    }

    /** Clean the session at the end of the SLO process. */
    private void cleanSession(final Session session) {
        logger.debug("End of SLO - Processing to session cleanup");
        session.remove(subjectMapping);
        session.remove(sessionIndexMapping);
        session.remove(authnContext);

        if (attributeMapping != null) {
            for (final String key : attributeMapping.keySet()) {
                session.remove(key);
            }
        }
    }

    private Response withError(HttpServletResponse response, String message) {
        final String msg = format("SSO Failed: %s", message);
        logger.error(msg);
        if (!response.isCommitted()) {
            return sendError(Status.INTERNAL_SERVER_ERROR, msg);
        }
        return RESPONSE_ALREADY_COMPLETED;
    }

    private static Response sendError(Status status, String message) {
        Response response = new Response(status);
        response.getEntity().setString(message);
        return response;
    }

    private static Response sendRedirect(String redirectUri) {
        // Redirect with a 302 (Found) status code
        Response response = new Response(Status.FOUND);
        // Web container was rebasing location header against server URL
        // Not useful if relayState is already (and always) an absolute URL
        response.getHeaders().put(LocationHeader.NAME, redirectUri);
        return response;
    }

    private HttpServletResponse adaptResponse(Context context) {
        AttributesContext attributesContext = context.asContext(AttributesContext.class);
        HttpServletResponse response = (HttpServletResponse) attributesContext.getAttributes()
                                                                              .get(HttpServletResponse.class.getName());
        try {
            return new ResponseAdapter(response);
        } catch (IOException e) {
            // If adaptation fails, returns the original object (there is always a servlet response around)
            logger.warn("Can't wrap original Servlet response, returning original response", e);
            return response;
        }
    }

    private static HttpServletRequest adaptRequest(Context context, Request req) {
        AttributesContext attributesContext = context.asContext(AttributesContext.class);
        HttpServletRequest request = (HttpServletRequest) attributesContext.getAttributes()
                                                                  .get(HttpServletRequest.class.getName());
        return new RequestAdapter(request, req);
    }

    /**
     * Reads the actual federation servlet from the JSON configuration file.
     */
    public static class Heaplet extends GenericHeaplet {
        @Override
        public Object create() throws HeapException {
            final JsonValue evaluated = config.as(evaluatedWithHeapProperties());
            final Map<String, String> attributeMapping = new HashMap<>();
            JsonValue mappings = evaluated.get("assertionMapping").expect(Map.class);
            if (mappings != null) {
                for (String key : mappings.keys()) { // TODO: 10/04/16 Do we want to evaluate the keys also ?
                    attributeMapping.put(key, mappings.get(key).asString());
                }
            }
            final String authnContextDelimiter = evaluated.get("authnContextDelimiter").defaultTo("|").asString();
            final String authnContext = evaluated.get("authnContext").asString();
            final String redirectURI = evaluated.get("redirectURI").asString();
            final String logoutURI = evaluated.get("logoutURI").asString();
            // Give subjectMapping and sessionIndexMapping a default value as they are needed when doing SP initiated
            // SLO
            final String subjectMapping = evaluated.get("subjectMapping").defaultTo("subjectMapping").asString();
            final String sessionIndexMapping = evaluated.get("sessionIndexMapping")
                                                        .defaultTo("sessionIndexMapping")
                                                        .asString();
            final String assertionConsumerEndpoint = evaluated.get("assertionConsumerEndpoint")
                                                              .defaultTo("fedletapplication")
                                                              .asString();
            final String sPinitiatedSSOEndpoint = evaluated.get("SPinitiatedSSOEndpoint")
                                                           .defaultTo("SPInitiatedSSO")
                                                           .asString();
            final String singleLogoutEndpoint = evaluated.get("singleLogoutEndpoint")
                                                         .defaultTo("fedletSloRedirect")
                                                         .asString();
            final String singleLogoutEndpointSoap = evaluated.get("singleLogoutEndpointSoap")
                                                             .defaultTo("fedletSloSoap")
                                                             .asString();
            final String sPinitiatedSLOEndpoint = evaluated.get("SPinitiatedSLOEndpoint")
                                                           .defaultTo("SPInitiatedSLO")
                                                           .asString();
            /*
             * Get the gateway configuration directory and set it as a system property to override the default openFed
             * location. Federation config files will reside in the SAML directory.
             */
            Environment environment = heap.get(ENVIRONMENT_HEAP_KEY, Environment.class);
            String samlDirectory = new File(environment.getBaseDirectory(), "SAML").getPath();
            logger.info("init directory: {}", samlDirectory);
            Properties p = System.getProperties();
            p.setProperty("com.sun.identity.fedlet.home", samlDirectory);
            System.setProperties(p);

            return new SamlFederationHandler(attributeMapping,
                                             subjectMapping,
                                             authnContextDelimiter,
                                             authnContext,
                                             sessionIndexMapping,
                                             redirectURI,
                                             logoutURI,
                                             assertionConsumerEndpoint,
                                             sPinitiatedSSOEndpoint,
                                             singleLogoutEndpoint,
                                             singleLogoutEndpointSoap,
                                             sPinitiatedSLOEndpoint);
        }

        @Override
        public void destroy() {
            // Automatically shutdown the fedlet
            ShutdownManager.getInstance().shutdown();
            super.destroy();
        }
    }
}
