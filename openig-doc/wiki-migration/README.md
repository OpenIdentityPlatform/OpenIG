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

Copyright 2026 3A Systems, LLC.
-->

# Deferred OpenIG wiki How-To migration

This is a review artifact. Do not apply or publish the wiki patch until the OpenIG documentation and companion documentation-site navigation have deployed and their pages, fragments and images have been checked on the live site. The build does not apply this patch.

Wiki repository: https://github.com/OpenIdentityPlatform/OpenIG.wiki.git

Source revision: `dcf9f150cac85b6feb937f17056b1650b3a9178c`.

The patch moves thirteen substantive pages, updates Home and How-To-Guides, and retains 85 section destinations (including the repeated HTTP-client heading and the Cyrillic OpenIG configuration anchor). Existing wiki images remain available. These are links to specific sections, not HTTP redirects.

## Inventory

| Wiki page | Source in `src/main/asciidoc/how-to/` |
| --- | --- |
| `How-To-Run-OpenIG-in-Kubernetes.md` | `how-to-run-openig-in-kubernetes.adoc` |
| `TIP:-How-to-Persist-OpenIG-Configuration-Between-Restarts.md` | `how-to-persist-openig-configuration-between-restarts.adoc` |
| `How-To-Protect-Web-Services-with-OpenIG.md` | `how-to-protect-web-services-with-openig.adoc` |
| `How-To-Protect-Kubernetes-Services-With-OpenIG.md` | `how-to-protect-kubernetes-services-with-openig.adoc` |
| `REST-API-Security:-OAuth-OIDC-Authorization,-OpenAPI-Swagger-Compliance-Validation,-Service-Level-Monitoring.md` | `rest-api-security-oauth-oidc-openapi-and-monitoring.adoc` |
| `How-to-Protect-WebSocket-Connection-with-OpenAM-and-OpenIG.md` | `how-to-protect-websocket-connection-with-openam-and-openig.adoc` |
| `How-to-Setup-API-Throughput-Control-(Throttling).md` | `how-to-setup-api-throughput-control-throttling.adoc` |
| `How-to-setup-ICAP-Filter-for-DLP-and-Antivirus-protection-with-OpenIG.md` | `how-to-setup-icap-filter-for-dlp-and-antivirus-protection.adoc` |
| `How-to-Integrate-OpenIG-and-Message-Brokers.md` | `how-to-integrate-openig-and-message-brokers.adoc` |
| `Convert-OpenAM-token-to-OpenID-Connect-(JWT).md` | `convert-openam-token-to-openid-connect-jwt.adoc` |
| `OpenIG-HTTP-client.md` | `openig-http-client.adoc` |
| `Prompt-Injection-Mitigation-in-AI-Systems-Using-API-Gateway.md` | `prompt-injection-mitigation-in-ai-systems-using-api-gateway.adoc` |
| `Схема-авторизации-доступа-к-защищенному-ресурсу-c-помощью-OpenAM-и-OpenIG.md` | `authorization-flow-with-openam-and-openig.adoc` |

The thirteen pages include REST API Security and the translated authorization-flow diagram, even though the former was absent from Home. Home's OpenAM authorization and tracing links point to the already published OpenAM How-To pages; those articles are not copied into OpenIG.

## Review and publication order

1. Publish the OpenIG generated documentation and `HowTo_Guides.pdf` through the existing deployment workflow.
2. Apply the separately reviewed companion site changes to `openig/antora.yml` and `ROOT/modules/ROOT/pages/index.adoc`, then publish and verify navigation, search, images and PDF links.
3. In a clean wiki checkout, review any changes since the source revision and refresh this patch if necessary. Check every retained section link against the live site.
4. Apply the patch locally, review the diff, and request separate approval for its commit and publication.

```sh
git apply --check /path/to/wiki-how-to.patch
git apply /path/to/wiki-how-to.patch
git diff --check
git diff
# Before committing, the reverse check must also succeed:
git apply --reverse --check /path/to/wiki-how-to.patch
```

After the wiki migration is published and verified, remove this deferred patch and its application instructions in a separate change.

## Migration notes

- Code listings are preserved with collision-free AsciiDoc block delimiters; local section IDs have article prefixes to avoid duplicate identifiers in the combined PDF.
- Only ten referenced wiki screenshots are copied into `images/how-to/`. The authorization diagram is translated and rendered as a local SVG; its text source is included in the article.
- Old section destinations are retained in wiki stubs, including `migrated-example-script-1`, `настройка--openig` and `icap-denial-of-service-in---dicaprewritetrue-mode`. The removed handwritten Table of Contents points to the new article's top-level anchor.
- The Kubernetes Ingress examples use `networking.k8s.io/v1` with `pathType` and a service backend; an Ingress controller is an explicit prerequisite. See the [Kubernetes Ingress reference](https://kubernetes.io/docs/concepts/services-networking/ingress/).
- Corrected the `Bearer` typo in the token-transformation route, the REST tutorial's configuration mount/directory and missing `openam` system property, the truncated OpenAM URL and sentence, the sample-service YAML path, shell continuations with trailing whitespace, and an invalid trailing comma and unfinished sentence in the WebSocket tutorial. The web-services tutorial also corrects Accept negotiation, the `/secure` response key, curl invocations and JSON error bodies. Its expired sample JWTs are retained with an explicit renewal note; example versions and other captured output remain historical.
- The Accept filter preserves upstream responses with an unparseable Content-Type or unknown charset. The prompt-injection filters produce valid JSON errors, and the LLM guard forwards requests only after a non-empty set of explicit SAFE decisions. The HTTP-to-Kafka example uses the same producer and topic throughout; the ZooKeeper settings explain the effect of setting both connection options.
- The old OpenAM `#openam-installation` link now targets the published article's `openam-setup` section. Own-guide references use version-aware xrefs; cross-component xrefs are validated by the full site build.
- Current-version PDF generation uses the existing doc-maven-plugin release. Historical PDF rebuilds need a published plugin release containing commons #387 and explicit documentation-version configuration; merging that converter change does not rewrite previously distributed PDFs.

## Local verification

After generating the documentation:

```sh
python3 -m unittest discover -s openig-doc -p 'test_check_how_to_links.py'
python3 openig-doc/check-how-to-links.py
```

The checker validates `xref:` macros and images in preprocessed How-To pages. It accepts relative paths and `<module>:<page>` references, and rejects `<<...>>` references outside verbatim blocks; use `xref:` so the Antora converter can rewrite links. Component-qualified links are checked by building the full Antora site. The regression suite also checks the deferred patch's local page URLs and all 85 original wiki section destinations using `src/test/resources/how-to-wiki-destinations.txt`; retain that compatibility fixture after removing the deferred patch. Executable Groovy tests cover Accept negotiation, signed JWT expiration and missing tokens, LLM guard decisions and JSON error responses. External services and integration tutorials require their own runtime environments and are not implied to have been exercised by a documentation build.
