# OpenIG Community Edition v2.1.0

# Why OpenIG?
OpenIG is an independent policy enforcement point that reduces the proliferation of passwords and ensures consistent, secure access across multiple web apps and APIs. OpenIG can leverage any standards-compliant identity provider to integrate into your current architecture. Single sign-on and sign-off improves the user experience and will vastly improve adoption rates and consumption of services provided.

- Extend SSO to any Application
- Federate Enabling Applications
- Implement Standards Based Policy Enforcement

### About the Community Version

ForgeRock created OpenIG Community Version from their End of Service Life Identity Platform. This code was first released as part of the ForgeRock Identity Platform in March 2012. 

To find out about the enterprise release of the ForgeRock platform [here][ForgeRock Identity Platform].

## How do I build it?


### Environment
The code is built using [Maven](https://maven.apache.org/) and is predominantly java based. 

Versions to use: 

Java - OpenJDK v1.6.0_41  
Maven - v3.0.5

The output of ```mvn --version``` should show the correct versions of both maven and java as below;

```
Apache Maven 3.0.5
Maven home: /usr/share/maven
Java version: 1.6.0_41, vendor: Sun Microsystems Inc.
Java home: /usr/lib/jvm/java-6-openjdk-amd64/jre
Default locale: en_GB, platform encoding: UTF-8
OS name: "linux", version: "3.13.0-108-generic", arch: "amd64", family: "unix"
```

### Building the Code
Clone the repository and build: 

```
git clone git@github.com:ForgeRock/openig-community-edition-2.1.0.git
cd openig-community-edition-2.1.0
mvn clean install
```

### Modifying the GitHub Project Page

The OpenIG Community Edition project pages are published via the gh-pages branch, which contains all the usual artifacts to create the web page. The GitHub page is served up directly from this branch by GitHub.

## Getting Started with OpenIG

ForgeRock provide a comprehensive set of documents for OpenIG. They maybe found [here][OpenIG CE Docs].

Web versions:

- [Guide to OpenIG](https://backstage.forgerock.com/docs/openig/2.1.0/gateway-guide)
- [OpenIG Reference](https://backstage.forgerock.com/docs/openig/2.1.0/reference)
- [OpenIG Release Notes](https://backstage.forgerock.com/docs/openig/2.1.0/release-notes)
- [JavaDoc](https://backstage.forgerock.com/static/docs/openig/2.1.0/javadocs)

## Issues

Issues are handled via the [GitHub issues page for the project](https://github.com/ForgeRock/openig-community-edition-2.1.0/issues).

## Security Policy

ForgeRock will create GitHub issues for any known security issues that are thought to affect the community edition. They will have a SECURITY label. Community members are responsible for fixing and testing any security issues.

## What should I do if I find a new security issue?

If you find a new security issue in the community edition please send an email describing the issue and how it may be reproduced to security@forgerock.com. Once we receive the email we will;

- Confirm whether or not the vulnerability affects any currently supported versions and if so we will follow our standard security response process which will involve us publishing the GitHub issue as part of the security advisory process
- If the issue does not affect any supported versions we will notify the reporter and request that they create a github issue directly


## How to Collaborate

Collaborate by:

- [Reporting an Issue][GitHub Issues]
- [Fix an Issue][Help Wanted Issues]
- [Contribute to the Wiki][Project Wiki]

Code collaboration is done by creating an issue, discussing the changes in the issue. When the issue's been agreed then, fork, modify, test and submmit a pull request. 

## Licensing

The Code an binaries are covered under the [CDDL 1.0 license](https://forgerock.org/cddlv1-0/).

# All the Links
To save you wading through the README looking for 'that' link...

- [GitHub Project]
- [Project Wiki]
- [GitHub Issues]
- [Help Wanted Issues]
- [Guide to OpenIG]
- [OpenIG Reference]
- [OpenIG Release Notes]
- [JavaDoc]


- [ForgeRock Identity Platform]

[GitHub Project]:https://github.com/ForgeRock/openig-community-edition-2.1.0
[GitHub Issues]: https://github.com/ForgeRock/openig-community-edition-2.1.0/issues
[Help Wanted Issues]:https://github.com/ForgeRock/openig-community-edition-2.1.0/labels/help%20wanted
[Project Wiki]:https://github.com/ForgeRock/openig-community-edition-2.1.0/wiki
[ForgeRock Identity Platform]:https://www.forgerock.com/platform/

[OpenIG CE Docs]:https://backstage.forgerock.com/docs/openig/2.1.0
[Guide to OpenIG]:https://backstage.forgerock.com/docs/openig/2.1.0/gateway-guide
[OpenIG Reference]:https://backstage.forgerock.com/docs/openig/2.1.0/reference
[OpenIG Release Notes]:https://backstage.forgerock.com/docs/openig/2.1.0/release-notes
[JavaDoc]:https://backstage.forgerock.com/static/docs/openig/2.1.0/javadocs