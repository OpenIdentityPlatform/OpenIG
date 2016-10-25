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

package org.forgerock.openig.doc;

import static com.gargoylesoftware.htmlunit.HttpMethod.GET;
import static com.gargoylesoftware.htmlunit.HttpMethod.POST;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.glassfish.grizzly.http.server.HttpServer;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.gargoylesoftware.htmlunit.FormEncodingType;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.util.NameValuePair;

@SuppressWarnings("javadoc")
public class SampleApplicationTest {

    private static final Logger logger = Logger.getLogger(SampleApplicationTest.class.getName());
    public static final String HOME_TITLE = "Sample application home page";
    private static final String LOGIN_TITLE = "Howdy, Anonymous User";
    private static final String PROFILE_TITLE = "Howdy, demo";

    private static HttpServer httpServer;
    private WebClient webClient;
    private String port;
    private String sslPort;

    private String httpServerPath;
    private String httpsServerPath;

    @BeforeTest
    public void setUp() throws Exception {
        port = System.getProperty("serverPort");
        sslPort = System.getProperty("serverSslPort");
        logger.info("Port: " + port + ", SSL Port: " + sslPort);
        httpServer = SampleApplication.start(Integer.parseInt(port), Integer.parseInt(sslPort));
        webClient = new WebClient();
        webClient.setUseInsecureSSL(true);
        httpServerPath = "http://localhost:" + port;
        httpsServerPath = "https://localhost:" + sslPort;
    }

    @AfterTest
    public void tearDown() throws Exception {
        webClient.closeAllWindows();
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        logger.info("Shutting down server");
        SampleApplication.stop(httpServer);
    }

    @Test
    public void testGetHomePage() throws Exception {
        logger.info("Testing equivalent of curl --verbose " + httpServerPath + "/home");

        final WebRequest webRequest = new WebRequest(new URL(httpServerPath + "/home"), GET);
        final WebResponse webResponse = webClient.loadWebResponse(webRequest);

        assertEquals(webResponse.getStatusCode(), 200);
        assertTrue(webResponse.getContentAsString().contains(HOME_TITLE));
    }

    @Test
    public void testGetLoginPage() throws Exception {
        // Check for HTTP 200 OK and the Login page in the body of the response
        logger.info("Testing equivalent of curl --verbose " + httpServerPath);

        final WebRequest webRequest = new WebRequest(new URL(httpServerPath), GET);
        final WebResponse webResponse = webClient.loadWebResponse(webRequest);

        assertEquals(webResponse.getStatusCode(), 200);
        assertTrue(webResponse.getContentAsString().contains(LOGIN_TITLE));
    }

    @Test
    public void testGetLoginPageHttps() throws Exception {
        // Given
        logger.info("Testing the equivalent of curl --verbose " + httpsServerPath);

        // When
        final WebRequest webRequest = new WebRequest(new URL(httpsServerPath), GET);
        final WebResponse webResponse = webClient.loadWebResponse(webRequest);

        // Then
        assertEquals(webResponse.getStatusCode(), 200);
        assertTrue(webResponse.getContentAsString().contains(LOGIN_TITLE));
    }

    @Test
    public void testPostValidCredentials() throws Exception {

        // Check for HTTP 200 OK and the username in the body of the response
        logger.info("Testing equivalent of "
                + "curl --verbose --data \"username=demo&password=changeit\" " + httpServerPath);

        final WebRequest webRequest = new WebRequest(new URL(httpServerPath), POST);
        webRequest.setEncodingType(FormEncodingType.URL_ENCODED);
        webRequest.setRequestBody("username=demo&password=changeit");
        final WebResponse webResponse = webClient.loadWebResponse(webRequest);

        assertEquals(webResponse.getStatusCode(), 200);
        assertTrue(webResponse.getContentAsString().contains("Howdy, demo"));
    }

    @Test
    public void testPostValidCredentialsAsHeaders() throws Exception {
        logger.info("Testing equivalent of "
                + "curl --verbose --H \"username: demo\" --H \"password=changeit\" " + httpServerPath);

        final WebRequest webRequest = new WebRequest(new URL(httpServerPath), POST);
        webRequest.setAdditionalHeader("username", "demo");
        webRequest.setAdditionalHeader("password", "changeit");
        final WebResponse webResponse = webClient.loadWebResponse(webRequest);

        assertEquals(webResponse.getStatusCode(), 200);
        assertTrue(webResponse.getContentAsString().contains(PROFILE_TITLE));
    }

    @Test
    public void postIncompleteCredentials() throws Exception {

        // Check for HTTP 401 Authorization Required
        logger.info("Testing equivalent of "
                + "curl --verbose --data \"username=no-password\" " + httpServerPath);

        final WebRequest webRequest = new WebRequest(new URL(httpServerPath), POST);
        webRequest.setEncodingType(FormEncodingType.URL_ENCODED);
        webRequest.setRequestBody("username=no-password");
        final WebResponse webResponse = webClient.loadWebResponse(webRequest);

        assertEquals(webResponse.getStatusCode(), 401);
    }

    @Test
    public void postInvalidCredentials() throws Exception {

        // Check for HTTP 403 Forbidden
        logger.info("Testing equivalent of "
                + "curl --verbose --data \"username=wrong&password=wrong\" " + httpServerPath);

        final WebRequest webRequest = new WebRequest(new URL(httpServerPath), POST);
        webRequest.setEncodingType(FormEncodingType.URL_ENCODED);
        webRequest.setRequestBody("username=wrong&password=wrong");
        final WebResponse webResponse = webClient.loadWebResponse(webRequest);

        assertEquals(webResponse.getStatusCode(), 403);
    }

    @Test
    public void testValidWebFingerRequest() throws Exception {
        logger.info("Testing equivalent of curl " + httpServerPath
                + "/.well-known/webfinger?resource=resource");
        final URL webFingerUrl = new URL(httpServerPath + "/.well-known/webfinger");

        final WebRequest webRequest = new WebRequest(webFingerUrl, GET);
        final List<NameValuePair> parameters = new ArrayList<>();
        parameters.add(new NameValuePair("resource", "resource"));
        webRequest.setRequestParameters(parameters);
        final WebResponse webResponse = webClient.loadWebResponse(webRequest);

        assertEquals(webResponse.getStatusCode(), 200);
        assertEquals(webResponse.getContentAsString(),
                "{ \"subject\": \"resource\", \"links\": "
                        + "[ { \"rel\": \"http://openid.net/specs/connect/1.0/issuer\", "
                        + "\"href\": \"http://openam.example.com:8088/openam/oauth2\" } ] }");
    }

    @Test
    public void testBrokenWebFingerRequest() throws Exception {
        logger.info("Testing equivalent of curl " + httpServerPath + "/.well-known/webfinger");

        final URL webFingerUrl = new URL(httpServerPath + "/.well-known/webfinger");
        final WebRequest webRequest = new WebRequest(webFingerUrl, GET);
        final WebResponse webResponse = webClient.loadWebResponse(webRequest);

        assertEquals(webResponse.getStatusCode(), 400);
        assertEquals(webResponse.getContentAsString(),
                "{ \"error\": \"Request must include a resource parameter.\" }");
    }
}
