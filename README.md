<!--
  The contents of this file are subject to the terms of the Common Development and
  Distribution License (the License). You may not use this file except in compliance with the
  License.

  You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
  specific language governing permission and limitations under the License.

  When distributing Covered Software, include this CDDL Header Notice in each file and include
  the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
  Header, with the fields enclosed by brackets [] replaced by your own identifying
  information: "Portions copyright [year] [name of copyright owner]".

  Copyright 2015 ForgeRock AS.
  -->
OpenIG
======

The **Open Identity Gateway** ([OpenIG](http://forgerock.org/openig)) is a high-performance reverse proxy server with
specialized session management and credential replay functionality.

**OpenIG** works together with [OpenAM](http://forgerock.org/openam) to integrate Web applications without the need to
modify the target application or the container that it runs in.

* Support for identity standards ([OAuth 2.0](https://tools.ietf.org/html/rfc6749), [OpenID Connect](http://openid.net/specs/openid-connect-core-1_0.html), [SAML 2.0](http://saml.xml.org/saml-specifications))
* Application and API gateway concept
* Prepackaged SAML 2.0-based federation
* Password capture and replay
* Works with any identity provider, including OpenAM
* Single Sign-On and Single Sign-Out
* 100% open source

Build and Run
=============

You need `git` and `maven` in order to get the source code and build it:
```
git clone ssh://git@stash.forgerock.org:7999/openig/openig.git
cd openig
mvn clean install
```

Executing the OpenIG build is as simple as:
```
cd openig-war
mvn jetty:run
```

And you should see something like:
```
...
THU NOV 19 16:33:40 CET 2015 (INFO) _Router
Added route 'sts' defined in file '.../config/routes/openam-sts-oidc-to-saml.json'
------------------------------
...
Started ServerConnector@61843cc8{HTTP/1.1}{0.0.0.0:8080}
Started @10366ms
Started Jetty Server
Starting scanner at interval of 10 seconds.
```

The next step is then to go to [http://localhost:8080](http://localhost:8080) where you'll see the OpenIG welcome page.

Configure
=========
If there was no `OPENIG_BASE` environment variable set, OpenIG uses `~/.openig` as home where it loads its configuration files.

See the [OpenIG guide](http://openig.forgerock.org/doc/bootstrap/gateway-guide/index.html) for examples and detailed explanations.

See also the [Reference Guide](http://openig.forgerock.org/doc/bootstrap/reference/index.html),
[Release Notes](http://openig.forgerock.org/doc/bootstrap/release-notes/index.html)
and [Javadoc](http://openig.forgerock.org/javadoc/index.html).

License
=======

**OpenIG** is licensed under [CDDL 1.0](legal/CDDLv1.0.txt) (COMMON DEVELOPMENT AND DISTRIBUTION LICENSE Version 1.0)
