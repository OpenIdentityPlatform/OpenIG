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
 * Copyright 2014-2015 ForgeRock AS.
 */

package org.forgerock.openig.doc;

import com.gargoylesoftware.htmlunit.FormEncodingType;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
import org.glassfish.grizzly.http.server.HttpServer;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@SuppressWarnings("javadoc")
public class SampleServerTest {

    private static WebClient webClient;
    private static URL serverUrl;
    private static URL httpsServerUrl;
    private static final Logger logger = Logger.getLogger(SampleServerTest.class.getName());
    private static String port;
    private static String sslPort;
    private static HttpServer httpServer;

    @BeforeTest
    public static void setUp() throws Exception {
        port = System.getProperty("serverPort");
        sslPort = System.getProperty("serverSslPort");
        logger.info("Port: " +  port + ", SSL Port: " + sslPort);
        httpServer = SampleServer.start(Integer.parseInt(port), Integer.parseInt(sslPort));

        webClient = new WebClient();
        webClient.setUseInsecureSSL(true);
        serverUrl = new URL("http://localhost:" + port);
        httpsServerUrl = new URL("https://localhost:" + sslPort);
    }

    @AfterTest
    public void tearDown() throws Exception {
        webClient.closeAllWindows();
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        logger.info("Shutting down server");
        SampleServer.stop(httpServer);
    }

    @Test
    public void testGetHomePage() throws Exception {

        // Check for HTTP 200 OK and the home page in the body of the response
        logger.info("Testing equivalent of "
                + "curl --verbose http://localhost:" + port);

        final WebRequest webRequest = new WebRequest(serverUrl, HttpMethod.GET);
        final WebResponse webResponse = webClient.loadWebResponse(webRequest);

        Assert.assertEquals(webResponse.getStatusCode(), 200);
        Assert.assertTrue(webResponse.getContentAsString().contains("Howdy, Anonymous User"));
    }

    @Test
    public void testGetHomePageHttps() throws Exception {
        logger.info("Testing the equivalent of curl --verbose " + httpsServerUrl);

        final WebRequest webRequest = new WebRequest(httpsServerUrl, HttpMethod.GET);
        final WebResponse webResponse = webClient.loadWebResponse(webRequest);

        Assert.assertEquals(webResponse.getStatusCode(), 200);
        Assert.assertTrue(webResponse.getContentAsString().contains("Howdy, Anonymous User"));
    }

    @Test
    public void testPostValidCredentials() throws Exception {

        // Check for HTTP 200 OK and the username in the body of the response
        logger.info("Testing equivalent of "
                + "curl --verbose --data \"username=demo&password=changeit\" http://localhost:" + port);

        final WebRequest webRequest = new WebRequest(serverUrl, HttpMethod.POST);
        webRequest.setEncodingType(FormEncodingType.URL_ENCODED);
        webRequest.setRequestBody("username=demo&password=changeit");
        final WebResponse webResponse = webClient.loadWebResponse(webRequest);

        Assert.assertEquals(webResponse.getStatusCode(), 200);
        Assert.assertTrue(webResponse.getContentAsString().contains("Howdy, demo"));
    }

    @Test
    public void testPostValidCredentialsAsHeaders() throws Exception {
        logger.info("Testing equivalent of "
                + "curl --verbose --H \"username: demo\" --H \"password=changeit\" http://localhost:" + port);

        final WebRequest webRequest = new WebRequest(serverUrl, HttpMethod.POST);
        webRequest.setAdditionalHeader("username", "demo");
        webRequest.setAdditionalHeader("password", "changeit");
        final WebResponse webResponse = webClient.loadWebResponse(webRequest);

        Assert.assertEquals(webResponse.getStatusCode(), 200);
        Assert.assertTrue(webResponse.getContentAsString().contains("Howdy, demo"));
    }

    @Test
    public void postIncompleteCredentials() throws Exception {

        // Check for HTTP 401 Authorization Required
        logger.info("Testing equivalent of "
                + "curl --verbose --data \"username=no-password\" http://localhost:" + port);

        final WebRequest webRequest = new WebRequest(serverUrl, HttpMethod.POST);
        webRequest.setEncodingType(FormEncodingType.URL_ENCODED);
        webRequest.setRequestBody("username=no-password");
        final WebResponse webResponse = webClient.loadWebResponse(webRequest);

        Assert.assertEquals(webResponse.getStatusCode(), 401);
    }

    @Test
    public void postInvalidCredentials() throws Exception {

        // Check for HTTP 403 Forbidden
        logger.info("Testing equivalent of "
                + "curl --verbose --data \"username=wrong&password=wrong\" http://localhost:" + port);

        final WebRequest webRequest = new WebRequest(serverUrl, HttpMethod.POST);
        webRequest.setEncodingType(FormEncodingType.URL_ENCODED);
        webRequest.setRequestBody("username=wrong&password=wrong");
        final WebResponse webResponse = webClient.loadWebResponse(webRequest);

        Assert.assertEquals(webResponse.getStatusCode(), 403);
    }

    @Test
    public void testValidWebFingerRequest() throws Exception {
        logger.info("Testing equivalent of curl http://localhost:" + port
                + "/.well-known/webfinger?resource=resource");
        final URL webFingerUrl = new URL("http://localhost:" + port + "/.well-known/webfinger");

        final WebRequest webRequest = new WebRequest(webFingerUrl, HttpMethod.GET);
        final List<NameValuePair> parameters = new ArrayList<>();
        parameters.add(new NameValuePair("resource", "resource"));
        webRequest.setRequestParameters(parameters);
        final WebResponse webResponse = webClient.loadWebResponse(webRequest);

        Assert.assertEquals(webResponse.getStatusCode(), 200);
        Assert.assertEquals(webResponse.getContentAsString(),
                "{ \"subject\": \"resource\", \"links\": "
                        + "[ { \"rel\": \"http://openid.net/specs/connect/1.0/issuer\", "
                        + "\"href\": \"http://openam.example.com:8088/openam/oauth2\" } ] }");
    }

    @Test
    public void testBrokenWebFingerRequest() throws Exception {
        logger.info("Testing equivalent of curl http://localhost:" + port + "/.well-known/webfinger");

        final URL webFingerUrl = new URL("http://localhost:" + port + "/.well-known/webfinger");
        final WebRequest webRequest = new WebRequest(webFingerUrl, HttpMethod.GET);
        final WebResponse webResponse = webClient.loadWebResponse(webRequest);

        Assert.assertEquals(webResponse.getStatusCode(), 400);
        Assert.assertEquals(webResponse.getContentAsString(),
                "{ \"error\": \"Request must include a resource parameter.\" }");
    }
}
