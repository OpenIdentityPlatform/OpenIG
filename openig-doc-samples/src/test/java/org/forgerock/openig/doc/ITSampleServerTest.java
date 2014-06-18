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
 * Copyright 2014 ForgeRock AS.
 */

package org.forgerock.openig.doc;


import com.gargoylesoftware.htmlunit.FormEncodingType;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;
import org.glassfish.grizzly.http.server.HttpServer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.URL;
import java.util.logging.Logger;

@SuppressWarnings("javadoc")
public class ITSampleServerTest {

    private WebClient webClient;
    private URL serverUrl;
    private static final Logger logger =
            Logger.getLogger(ITSampleServerTest.class.getName());
    private static String port;
    private static HttpServer httpServer;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        port = System.getProperty("serverPort");
        httpServer = SampleServer.start(Integer.parseInt(port));
    }

    @Before
    public void setUp() throws Exception {
        webClient = new WebClient();
        serverUrl = new URL("http://localhost:" + port);
    }

    @After
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
}
