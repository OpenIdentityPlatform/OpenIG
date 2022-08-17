## <img alt="OpenIG Logo" src="https://github.com/OpenIdentityPlatform/OpenIG/raw/master/logo.png" width="300"/>
[![Latest release](https://img.shields.io/github/release/OpenIdentityPlatform/OpenIG.svg)](https://github.com/OpenIdentityPlatform/OpenIG/releases)
[![Build](https://github.com/OpenIdentityPlatform/OpenIG/actions/workflows/build.yml/badge.svg)](https://github.com/OpenIdentityPlatform/OpenIG/actions/workflows/build.yml)
[![Deploy](https://github.com/OpenIdentityPlatform/OpenIG/actions/workflows/deploy.yml/badge.svg)](https://github.com/OpenIdentityPlatform/OpenIG/actions/workflows/deploy.yml)
[![Issues](https://img.shields.io/github/issues/OpenIdentityPlatform/OpenIG.svg)](https://github.com/OpenIdentityPlatform/OpenIG/issues)
[![Last commit](https://img.shields.io/github/last-commit/OpenIdentityPlatform/OpenIG.svg)](https://github.com/OpenIdentityPlatform/OpenIG/commits/master)
[![License](https://img.shields.io/badge/license-CDDL-blue.svg)](https://github.com/OpenIdentityPlatform/OpenIG/blob/master/LICENSE.md)
[![Gitter](https://img.shields.io/gitter/room/nwjs/nw.js.svg)](https://gitter.im/OpenIdentityPlatform/OpenIG)
[![Top language](https://img.shields.io/github/languages/top/OpenIdentityPlatform/OpenIG.svg)](https://github.com/OpenIdentityPlatform/OpenIG)
[![Code size in bytes](https://img.shields.io/github/languages/code-size/OpenIdentityPlatform/OpenIG.svg)](https://github.com/OpenIdentityPlatform/OpenIG)

The Open Identity Gateway (OpenIG) is a high-performance reverse proxy server with specialized session management and credential replay functionality.

OpenIG is an independent policy enforcement point that reduces the proliferation of passwords and ensures consistent, secure access across multiple web apps and APIs. OpenIG can leverage any standards-compliant identity provider to integrate into your current architecture. Single sign-on and sign-off improves the user experience and will vastly improve adoption rates and consumption of services provided.
* Extend SSO to any Application
* Federate Enabling Applications
* Implement Standards Based Policy Enforcement

### How it Works
OpenIG is essentially a Java-based reverse proxy which runs as a web application. All HTTP traffic to each protected application is routed through OpenIG, enabling close inspection, transformation and filtering of each request. You can create new filters and handlers to modify the HTTP requests on their way through OpenIG, providing the ability to recognize login pages, submit login forms, transform or filter content, and even function as a Federation endpoint for the application. All these features are possible without making any changes to the application's deployment container or the application itself.

OpenIG works together with [OpenAM](https://github.com/OpenIdentityPlatform/OpenAM/blob/master/README.md) to integrate Web applications without the need to
modify the target application or the container that it runs in.

* Support for identity standards ([OAuth 2.0](https://tools.ietf.org/html/rfc6749), [OpenID Connect](http://openid.net/specs/openid-connect-core-1_0.html), [SAML 2.0](http://saml.xml.org/saml-specifications))
* Application and API gateway concept
* Prepackaged SAML 2.0-based federation
* Password capture and replay
* Works with any identity provider, including OpenAM
* Single Sign-On and Single Log-Out
* 100% open source


## License
This project is licensed under the [Common Development and Distribution License (CDDL)](https://github.com/OpenIdentityPlatform/OpenIG/blob/master/LICENSE.md). 

## Downloads 
* [OpenIG WAR](https://github.com/OpenIdentityPlatform/OpenIG/releases) (All OS)
* [OpenIG Docker](https://hub.docker.com/r/openidentityplatform/openig/) (All OS)

Java 1.8+ required

## How-to build
For windows use:
```bash
git config --system core.longpaths true
```

```bash
git clone --recursive  https://github.com/OpenIdentityPlatform/OpenIG.git
mvn -DskipTests -Dmaven.javadoc.skip=true install -f OpenIG
mvn install -f OpenIG
```

## How-to run after build
```bash
mvn -f OpenIG/openig-war clean package cargo:run
```

The next step is then to go to [http://localhost:8080](http://localhost:8080) where you'll see the OpenIG welcome page.

## Support and Mailing List Information
* OpenIG Community Wiki: https://github.com/OpenIdentityPlatform/OpenIG/wiki
* OpenIG Community Mailing List: open-identity-platform-openig@googlegroups.com
* OpenIG Community Archive: https://groups.google.com/d/forum/open-identity-platform-openig
* OpenIG Community on Gitter: https://gitter.im/OpenIdentityPlatform/OpenIG
* OpenIG Commercial support RFP: support@3a-systems.ru (English, Russian)

## Contributing
Please, make [Pull request](https://github.com/OpenIdentityPlatform/OpenIG/pulls)

## Thanks for OpenIG
* Forgerock OpenIG
