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

  Copyright 2015-2016 ForgeRock AS.
  -->

# OpenIG - The Open Identity Gateway

The Open Identity Gateway ([OpenIG](http://forgerock.org/openig)) is a high-performance reverse proxy server with
specialized session management and credential replay functionality.

OpenIG works together with [OpenAM](http://forgerock.org/openam) to integrate Web applications without the need to
modify the target application or the container that it runs in.

* Support for identity standards ([OAuth 2.0](https://tools.ietf.org/html/rfc6749), [OpenID Connect](http://openid.net/specs/openid-connect-core-1_0.html), [SAML 2.0](http://saml.xml.org/saml-specifications))
* Application and API gateway concept
* Prepackaged SAML 2.0-based federation
* Password capture and replay
* Works with any identity provider, including OpenAM
* Single Sign-On and Single Log-Out
* 100% open source

The project is led by ForgeRock who integrate the [OpenAM][openam_project_page], [OpenIDM][project_page], 
[OpenDJ][opendj_project_page], [OpenICF][openicf_project_page], and [OpenIG][openig_project_page] open source projects 
to provide a quality-assured [ForgeRock Identity Platform][identity_platform]. Support, professional services, and 
training are available for the Identity Platform, providing stability and safety for the management of your digital 
identities.

To find out more about the services ForgeRock provides, visit [www.forgerock.com][commercial_site].

To view the OpenDJ project page, which also contains all of the documentation, visit
 [https://forgerock.org/openig/][project_page]. 
 
For a great place to start, take a look at the [OpenIG Gateway Guide][gateway_guide].

For further help and discussion, visit the [community forums][community_forum]. 

# Getting the Open Identity Gateway

You can obtain the OpenIG Application in one of two ways:

## Download It

The easiest way to try OpenIG is to download the binary file and follow the [Gateway Guide][gateway_guide]. 

You can download either:

1. An [enterprise release build][enterprise_builds].
2. The [nightly build][nightly_builds] which contains the latest features and bug fixes, but may also contain 
_in progress_ unstable features.

## Build the Source Code

In order to build the project from the command line follow these steps:

### Prepare your Environment

To build OpenIG you will need the following installed on the machine you're going to build on:

Software               | Required Version
---------------------- | ----------------
Java JDK Version       | 7 and above (see below)
Git                    | 1.7.6 and above
Maven                  | 3.1.0 and above

ForgeRock does not support the use of Java 9 for running OpenIG in production, but it is fine for building the code.

You should also set the following environment variables for the majority of versions;

JAVA_HOME - set to the directory in which your SDK is installed  
MAVEN_OPTS  - When building with Java 7 set this to '-Xmx1g -XX:MaxPermSize=512m'. Java 8 and above does not support 
MaxPermSize so set this to '-Xmx1g'.

### Getting the Code

The central project repository lives on the ForgeRock Bitbucket Server at 
[https://stash.forgerock.org/projects/OPENIG][central_repo].

Mirrors exist elsewhere (for example GitHub) but all contributions to the project are managed by using pull requests 
to the central repository.

There are two ways to get the code - if you want to run the code unmodified you can clone the central repo (or a 
reputable mirror):

```
git clone https://stash.forgerock.org/scm/openig/openig.git
```

If, however, you are considering contributing bug fixes, enhancements, or modifying the code you should fork the project
 and then clone your private fork, as described below:

1. Create an account on [BackStage][backstage] - You can use these credentials to create pull requests, report bugs,
 and download the enterprise release builds.
2. Log in to the Bitbucket Server using your BackStage account credentials. 
3. Fork the [`openig`][central_repo] repository. This will create a fork for you in your own area of Bitbucket Server. Click on your
 profile icon then select 'view profile' to see all your forks. 
4. Clone your fork to your machine;
`git clone https://stash.forgerock.org/scm/~username/openig.git`  

Obtaining the code this way will allow you to create pull requests later. 

### Building the Code

The OpenIG build process and dependencies are managed by Maven. The first time you build the project, Maven will pull 
down all the dependencies and Maven plugins required by the build, which can take a significant amount of time. 
Subsequent builds will be much faster!

```
$ cd openig
$ mvn clean install
```

Executing the OpenIG build is as simple as:

```
mvn -pl openig-war jetty:run
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


## Getting Started With OpenIG

ForgeRock provide a comprehensive set of documents for OpenIG, including the nightly docs
[gateway guide][gateway_guide], [Reference Guide][reference_guide], [Release Notes][release_notes] and  [Javadocs][javadoc]:

- [Documentation for enterprise builds][enterprise_docs]
- [Draft docs for nightly builds and self built code][nightly_docs]

## OpenIG Studio

OpenIG comes bundled with a web studio to help you set up your route definitions. OpenIG Studio is an easy-to-use
interface for configuring routes, replacing the complexity of manually writing JSON files.

Use OpenIG Studio to configure:
* Inbound / Outbound Message Capture (Debugging)
* Throttling (Rate Limitation)
* Authentication (OpenID Connect)
* Authorization (Policy Enforcement Point)
* Monitoring (Runtime Statistics)

When you’ve created your routes in OpenIG Studio, it takes only a single click to deploy (and undeploy) them on the
current OpenIG instance.

For advanced configurations, you can always grab the generated JSON, copy/paste it into your favorite IDE, and edit
it manually. In this case, you'll need to deploy the configuration by copying the modified JSON into a file that will
be deployed by OpenIG, or POSTing the JSON through one of the routers' endpoints.

Give OpenIG Studio a try on [http://localhost:8080/openig/studio](http://localhost:8080/openig/studio).

## Contributing

There are many ways to contribute to the OpenIG project. You can contribute to the [OpenIG Docs Project][docs_project], 
report or [submit bug fixes][issue_tracking], or [contribute extensions][contribute] such as custom authentication 
modules, authentication scripts, policy scripts, dev ops scripts, and more.

## Versioning

ForgeRock produce an enterprise point release build. These builds use the versioning format X.0.0 (for example 3.0.0, 
4.0.0) and are produced yearly. These builds are free to use for trials, proof of concept projects and so on. A license
 is required to use these builds in production.

Users with support contracts have access to sustaining releases that contain bug and security fixes. These builds use 
the versioning format 3.0.x (for example 3.1.1 is a sustaining release). Users with support contracts also get access to 
quality-assured interim releases, such as the forthcoming OpenIG 4.5.0. 

## Authors

See the list of [contributors][contributors] who participated in this project.

## License

This project is licensed under the Common Development and Distribution License (CDDL). The following text applies to 
both this file, and should also be included in all files in the project:

> The contents of this file are subject to the terms of the Common Development and  Distribution License (the License). 
> You may not use this file except in compliance with the License.  
>   
> You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the specific language governing 
> permission and limitations under the License.  
>  
> When distributing Covered Software, include this CDDL Header Notice in each file and include the License file at 
> legal/CDDLv1.0.txt. If applicable, add the following below the CDDL Header, with the fields enclosed by brackets [] 
> replaced by your own identifying information: "Portions copyright [year] [name of copyright owner]".  
>   
> Copyright 2016 ForgeRock AS.    


## All the Links!
To save you sifting through the readme looking for 'that link'...

- [ForgeRock's commercial website][commercial_site]
- [ForgeRock's community website][community_site]
- [ForgeRock's BackStage server][backstage] 
- [ForgeRock Identity Platform][identity_platform]
- [OpenAM Project Page][openam_project_page]
- [OpenDJ Project Page][opendj_project_page]
- [OpenIDM Project Page][project_page]
- [OpenICF Project Page][openicf_project_page]
- [OpenIG Project Page][openig_project_page]
- [Community Forums][community_forum]
- [Gateway Guide][gateway_guide]
- [Reference Guide][reference_guide]
- [Release Notes][release_notes]
- [Javadocs][javadoc]
- [Enterprise Build Downloads][enterprise_builds]
- [Enterprise Documentation][enterprise_docs]
- [Nightly Build Downloads][nightly_builds]
- [Nightly Documentation][nightly_docs]
- [Central Project Repository][central_repo]
- [Issue Tracking][issue_tracking]
- [Contributors][contributors]
- [Coding Standards][coding_standards]
- [Contributions][contribute]
- [How to Buy][how_to_buy]

[commercial_site]: https://www.forgerock.com
[community_site]: https://www.forgerock.org
[backstage]: https://backstage.forgerock.com
[identity_platform]: https://www.forgerock.com/platform/
[openam_project_page]: https://forgerock.org/openam/
[opendj_project_page]: https://forgerock.org/opendj/
[openig_project_page]: https://forgerock.org/openig/
[project_page]: https://forgerock.org/openig/
[openicf_project_page]: https://forgerock.org/openicf/
[community_forum]: https://forgerock.org/forum/fr-projects/opendj/
[gateway_guide]: https://forgerock.org/openig/doc/bootstrap/gateway-guide/index.html
[reference_guide]: http://openig.forgerock.org/doc/bootstrap/reference/index.html
[release_notes]: http://openig.forgerock.org/doc/bootstrap/release-notes/index.html
[javadoc]:http://openig.forgerock.org/javadoc/index.html
[enterprise_builds]: https://backstage.forgerock.com/#!/downloads/OpenIG/OpenIG%20Enterprise#browse
[enterprise_docs]: https://backstage.forgerock.com/#!/docs/openig
[nightly_builds]: https://forgerock.org/downloads/openig-builds/
[nightly_docs]: https://forgerock.org/documentation/openig/
[central_repo]: https://stash.forgerock.org/projects/OPENIG/repos/openig/browse
[issue_tracking]: https://bugster.forgerock.org/jira/browse/OPENIG/?selectedTab=com.atlassian.jira.jira-projects-plugin:summary-panel
[docs_project]: https://stash.forgerock.org/projects/OPENIG/repos/opendj-docs/browse
[contributors]: https://stash.forgerock.org/plugins/servlet/graphs?graph=contributors&projectKey=OPENIG&repoSlug=openig&refId=all-branches&type=c&group=weeks
[coding_standards]: https://wikis.forgerock.org/confluence/display/devcom/Coding+Style+and+Guidelines
[how_to_buy]: https://www.forgerock.com/platform/how-buy/
[contribute]: https://forgerock.org/projects/contribute/

## Acknowledgments

* Sun Microsystems.
* The founders of ForgeRock.
* The good things in life.
