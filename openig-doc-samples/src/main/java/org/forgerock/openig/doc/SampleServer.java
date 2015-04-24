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

import org.glassfish.grizzly.http.Cookie;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple servlet allowing user-agents to get a home page,
 * and to post form-based login to access a protected profile page.
 */
public final class SampleServer {

    private static final String EOL = System.getProperty("line.separator");
    private static final Logger LOGGER = Logger.getLogger(SampleServer.class.getName());
    private static final int DEFAULT_PORT = 8081;
    private static final int DEFAULT_SSL_PORT = 8444;

    /**
     * Start an HTTP server.
     *
     * @param args Optionally specify a free port number and free SSL port number.
     *             Defaults: 8081 (HTTP), 8444 (HTTPS).
     */
    public static void main(String[] args) {
        final String usage = "Optionally specify HTTP and HTTPS port numbers. Defaults: 8081, 8444.";
        int port = DEFAULT_PORT;
        int sslPort = DEFAULT_SSL_PORT;

        if (args.length > 2) {
            System.out.println(usage);
            System.exit(-1);
        }

        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
            if (args.length == 2) {
                sslPort = Integer.parseInt(args[1]);
            }
        }

        LOGGER.setLevel(Level.INFO);
        runServer(port, sslPort);
    }

    /**
     * Run the HTTP server, listening on the chosen port.
     * <p>
     * On HTTP GET the server returns a home page with a login form.
     * <p>
     * On HTTP PUT with valid credentials, the server returns a profile page.
     *
     * @param port      Port on which the server listens for HTTP
     * @param sslPort   Port on which the server listens for HTTPS
     */
    static void runServer(int port, int sslPort) {
        start(port, sslPort, true);
    }

    /**
     * Run the HTTP server, listening on the chosen port.
     * <p>
     * Use stop() to shut the server down.
     *
     * @param port Port on which the server listens for HTTP
     * @return The HttpServer that is running if letRun is true
     */
    static HttpServer start(final int port, final int sslPort) {
        return start(port, sslPort, false);
    }

    /**
     * Run the HTTP server, listening on the chosen port.
     *
     * @param port          Port on which the server listens for HTTP
     * @param sslPort       Port on which the server listens for HTTPS
     * @param waitForCtrlC  If true, only stop the server when the user enters Ctrl+C
     * @return The HttpServer that is running if letRun is true
     */
    static HttpServer start(final int port, final int sslPort, final boolean waitForCtrlC) {

        final HttpServer httpServer = new HttpServer();
        System.out.println("Preparing to listen for HTTP on port " + port + ".");
        httpServer.addListener(new NetworkListener("HTTP", "0.0.0.0", port));
        SSLEngineConfigurator sslEngineConfigurator = createSslConfiguration();
        if (sslEngineConfigurator != null) {
            System.out.println("Preparing to listen for HTTPS on port " + sslPort + ".");
            System.out.println("The server will use a self-signed certificate not known to browsers.");
            System.out.println("When using HTTPS with curl for example, try --insecure.");
            httpServer.addListener(new NetworkListener("HTTPS", "0.0.0.0", sslPort));
            httpServer.getListener("HTTPS").setSSLEngineConfig(sslEngineConfigurator);
            httpServer.getListener("HTTPS").setSecure(true);
        }
        httpServer.getServerConfiguration().addHttpHandler(new SampleHandler());

        if (waitForCtrlC) {
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run() {
                    httpServer.shutdownNow();
                }
            }, "shutDownHook"));
        }

        try {
            System.out.println("Starting server...");
            httpServer.start();
            if (waitForCtrlC) {
                System.out.println("Press Ctrl+C to stop the server.");
                Thread.currentThread().join();
            }
        } catch (Exception e) {
            LOGGER.info(e.getMessage());
        }

        return httpServer;
    }

    /**
     * Stop the HTTP Server started with waitForCtrlC set to false.
     *
     * @param httpServer The server to stop
     */
    static void stop(final HttpServer httpServer) {
        httpServer.shutdownNow();
    }

    /**
     * Returns an SSL configuration that uses {@code keystore.jks}.
     *
     * <br>
     *
     * The key store has one key pair with a self-signed cert, {@code test}.
     * The key store was set up with the following commands:
     *
     * <pre>
     * keytool \
     *  -genkey \
     *  -alias test \
     *  -keyalg rsa \
     *  -dname "cn=Doc Sample Server,dc=openig,dc=example,dc=com" \
     *  -keystore keystore.jks \
     *  -storepass changeit \
     *  -keypass changeit
     *
     * keytool \
     *  -selfcert \
     *  -alias test \
     *  -validity 7300 \
     *  -keystore keystore.jks \
     *  -storepass changeit \
     *  -keypass changeit
     * </pre>
     *
     * @return An SSL configuration that uses {@code keystore.jks},
     *         or null if no valid SSL configuration could be set up.
     */
    private static SSLEngineConfigurator createSslConfiguration() {
        SSLContextConfigurator sslContextConfigurator = new SSLContextConfigurator();
        try {
            // Cannot read the key store as a file inside a jar,
            // so get the key store as a byte array.
            sslContextConfigurator.setKeyStoreBytes(getKeyStore());
            sslContextConfigurator.setKeyStorePass("changeit");
            sslContextConfigurator.setKeyPass("changeit");
        } catch (IOException e) {
            LOGGER.info("Failed to load key store when setting up HTTPS.");
            e.printStackTrace();
            return null;
        }

        if (sslContextConfigurator.validateConfiguration(true)) {
            SSLEngineConfigurator sslEngineConfigurator =
                    new SSLEngineConfigurator(sslContextConfigurator.createSSLContext());
            sslEngineConfigurator.setClientMode(false);
            sslEngineConfigurator.setNeedClientAuth(false);
            sslEngineConfigurator.setWantClientAuth(false);
            return sslEngineConfigurator;
        } else {
            LOGGER.info("Failed to build a valid HTTPS configuration.");
            return null;
        }
    }

    /**
     * Returns {@code keystore.jks} as a byte array.
     * @return {@code keystore.jks} as a byte array.
     * @throws IOException  Failed to read {@code keystore.jks}.
     */
    private static byte[] getKeyStore() throws IOException {
        InputStream inputStream = SampleServer.class.getResourceAsStream("/keystore.jks");
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int read;
        byte[] data = new byte[4096];
        while ((read = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, read);
        }
        return buffer.toByteArray();
    }

    /**
     * Handler for HTTP GET and HTTP PUT requests.
     */
    static class SampleHandler extends HttpHandler {

        @Override
        public void service(Request request, Response response) throws Exception {
            if (request.getHttpHandlerPath().equalsIgnoreCase("/login")) {
                response.addCookie(new Cookie("login-cookie", "chocolate chip"));
            }

            if (Method.GET == request.getMethod()) {
                String homePage = getResourceAsString("/home.html");

                response.setContentType("text/html");
                response.setStatus(200, "OK");
                response.setContentLength(homePage.length());
                response.getWriter().write(homePage);
            }

            if (Method.POST == request.getMethod()) {
                String username;
                String password;

                // Allow use of IDToken1 (username) and IDToken2 (password)
                // to simulate the behavior of the OpenAM classic UI login page.
                username = request.getParameter("IDToken1");
                password = request.getParameter("IDToken2");
                if (username != null && password != null) {
                    simulateOpenAMResponse(username, password, response);
                    return;
                }

                // Accept username and password as headers for testing.
                if (notNullOrEmpty(request.getHeader("username"))) {
                    username = request.getHeader("username");
                }
                if (notNullOrEmpty(request.getHeader("password"))) {
                    password = request.getHeader("password");
                }

                // Accept username and password as parameters
                // in the query string or as form-encoded data.
                if (notNullOrEmpty(request.getParameter("username"))) {
                    username = request.getParameter("username");
                }
                if (notNullOrEmpty(request.getParameter("password"))) {
                    password = request.getParameter("password");
                }

                if (username == null || password == null) {
                    final String authRequired = "Authorization Required";
                    response.setStatus(401, authRequired);
                    response.setContentLength(authRequired.length() + EOL.length());
                    response.getWriter().write(authRequired + EOL);
                    return;
                }

                if (credentialsAreValid(username, password)) {

                    // Replace profile page placeholders and respond.
                    final StringBuilder headers = new StringBuilder();
                    for (String name : request.getHeaderNames()) {
                        for (String header : request.getHeaders(name)) {
                            headers.append(name)
                                    .append(": ")
                                    .append(header)
                                    .append("<br>");
                        }
                    }

                    String profilePage = getResourceAsString("/profile.html")
                            .replaceAll(EOL, "####")
                            .replaceAll("USERNAME", username)
                            .replace("METHOD", request.getMethod().getMethodString())
                            .replace("REQUEST_URI", request.getDecodedRequestURI())
                            .replace("HEADERS", headers.toString())
                            .replaceAll("####", EOL);

                    response.setContentType("text/html");
                    response.setStatus(200, "OK");
                    response.setContentLength(profilePage.length());
                    response.getWriter().write(profilePage);

                } else {
                    final String forbidden = "Forbidden";
                    response.setStatus(403, forbidden);
                    response.setContentLength(forbidden.length() + EOL.length());
                    response.getWriter().write(forbidden + EOL);
                }
            }
        }

        /**
         * Returns true if the String to test is neither null nor empty.
         * @param s The String to test.
         * @return true if the String to test is neither null nor empty.
         */
        private boolean notNullOrEmpty(final String s) {
            return s != null && !s.isEmpty();
        }
    }

    /**
     * Simulates a response from OpenAM.
     *
     * <br>
     *
     * If the username and password are valid, sets response status to 200 OK
     * and writes a text message and fake SSO Token cookie in the response.
     *
     * Otherwise, sets reponse status to 403 Forbidden
     * and writes a failure text message in the response.
     *
     * @param username      A username such as {@code demo}
     * @param password      A password such as {@code changeit}
     * @param response      The response to the request
     * @throws IOException  Failed when checking credentials
     */
    private static void simulateOpenAMResponse(final String username,
                                               final String password,
                                               final Response response) throws IOException {
        String message;
        if (credentialsAreValid(username, password)) {
            message = "Welcome, " + username + "!" + EOL;
            response.addCookie(new Cookie("iPlanetDirectoryPro", "fakeSsoToken"));
            response.setStatus(200, "OK");
        } else {
            message = "Too bad, " + username + ", you failed to authenticate." + EOL;
            response.setStatus(403, "Forbidden");
        }
        response.setContentType("text/plain");
        response.setContentLength(message.length());
        response.getWriter().write(message);
    }

    /**
     * Check whether username and password credentials are valid.
     *
     * @param username A username such as {@code demo}
     * @param password A password such as {@code changeit}
     *
     * @return True if the username matches the password in credentials.properties
     * @throws java.io.IOException Could not read credentials.properties
     */
    static synchronized boolean credentialsAreValid(
            final String username, final String password)
            throws IOException {

        boolean result = false;

        Properties credentials = new Properties();
        InputStream in = SampleHandler.class.getResourceAsStream("/credentials.properties");
        credentials.load(in);

        final String pwd = credentials.getProperty(username);
        if (pwd != null) {
            result = pwd.equals(password);
        }

        in.close();

        return result;
    }

    /**
     * Read the contents of a resource file into a string.
     *
     * @param resource Path to resource file
     * @return String holding the content of the resource file
     */
    static synchronized String getResourceAsString(final String resource) {

        StringBuilder content = new StringBuilder();
        InputStream inputStream = SampleHandler.class.getResourceAsStream(resource);

        Scanner scanner = null;
        try {
            scanner = new Scanner(inputStream);
            while (scanner.hasNextLine()) {
                content.append(scanner.nextLine()).append(EOL);
            }
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }

        return content.toString();
    }

    /**
     * Not used.
     */
    private SampleServer() {
    }
}
