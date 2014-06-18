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

import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;

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

    /**
     * Start an HTTP server.
     *
     * @param args Optionally specify a free port number. Default: 8081.
     */
    public static void main(String[] args) {
        final String usage = "Specify an optional port number. Default: 8081.";
        int port = 8081;

        if (args.length > 1) {
            System.out.println(usage);
            System.exit(-1);
        }

        if (args.length == 1) {
            port = Integer.parseInt(args[0]);
        }

        LOGGER.setLevel(Level.INFO);
        runServer(port);
    }

    /**
     * Run the HTTP server, listening on the chosen port.
     * <p>
     * On HTTP GET the server returns a home page with a login form.
     * <p>
     * On HTTP PUT with valid credentials, the server returns a profile page.
     *
     * @param port Port on which the server listens
     */
    static void runServer(int port) {
        start(port, true);
    }

    /**
     * Run the HTTP server, listening on the chosen port.
     * <p>
     * Use stop() to shut the server down.
     *
     * @param port Port on which the server listens
     * @return The HttpServer that is running if letRun is true
     */
    static HttpServer start(final int port) {
        return start(port, false);
    }

    /**
     * Run the HTTP server, listening on the chosen port.
     *
     * @param port Port on which the server listens
     * @param waitForCtrlC If true, only stop the server when the user enters Ctrl+C
     * @return The HttpServer that is running if letRun is true
     */
    static HttpServer start(final int port, final boolean waitForCtrlC) {

        final HttpServer httpServer = new HttpServer();
        final NetworkListener networkListener =
                new NetworkListener("sample-server", "0.0.0.0", port);
        httpServer.addListener(networkListener);
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
            LOGGER.info("Starting HTTP server on port " + port);
            httpServer.start();
            if (waitForCtrlC) {
                LOGGER.info("Press Ctrl+C to stop the server.");
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
     * Handler for HTTP GET and HTTP PUT requests.
     */
    static class SampleHandler extends HttpHandler {

        @Override
        public void service(Request request, Response response) throws Exception {
            if (Method.GET == request.getMethod()) {
                String homePage = getResourceAsString("/home.html");

                response.setContentType("text/html");
                response.setStatus(200, "OK");
                response.setContentLength(homePage.length());
                response.getWriter().write(homePage);
            }

            if (Method.POST == request.getMethod()) {

                final String username = request.getParameter("username");
                final String password = request.getParameter("password");

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
