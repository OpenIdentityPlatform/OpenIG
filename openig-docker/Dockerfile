# The contents of this file are subject to the terms of the Common Development and
# Distribution License (the License). You may not use this file except in compliance with the
# License.
#
# You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
# specific language governing permission and limitations under the License.
#
# When distributing Covered Software, include this CDDL Header Notice in each file and include
# the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
# Header, with the fields enclosed by brackets [] replaced by your own identifying
# information: "Portions copyright [year] [name of copyright owner]".
#
# Portions Copyright 2026 3A Systems, LLC.

FROM tomcat:11-jre25

LABEL org.opencontainers.image.authors="Open Identity Platform Community"

ARG VERSION

ENV CATALINA_HOME=/usr/local/tomcat
ENV OPENIG_USER="openig"
ENV OPENIG_BASE=/var/openig
ENV PATH=$CATALINA_HOME/bin:$PATH
ENV MEMORY="-server"
ENV CATALINA_OPTS="$MEMORY"

RUN rm -fr $CATALINA_HOME/webapps/*

#COPY openig-war/target/OpenIG-*.war $CATALINA_HOME/webapps/ROOT.war

# /usr/bin/pebble ships with the Ubuntu base image outside dpkg, so apt-get upgrade never patches
# its Go runtime; the image runs catalina.sh, not pebble, so it is deleted from the container
# filesystem (the base layer still carries it; Trivy applies whiteouts)
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get upgrade -y \
    && apt-get install -y --no-install-recommends curl unzip \
    && if [ -z "$VERSION" ] ; then VERSION="$(curl -i -o - --silent https://api.github.com/repos/OpenIdentityPlatform/OpenIG/releases/latest | grep -m1 "\"name\"" | cut -d\" -f4)"; fi \
    && if [ ! -f "$CATALINA_HOME/webapps/ROOT.war" ]; then curl -L https://github.com/OpenIdentityPlatform/OpenIG/releases/download/$VERSION/OpenIG-$VERSION.war --output $CATALINA_HOME/webapps/ROOT.war; fi \
    && useradd -m -r -u 1001 -g root $OPENIG_USER \
    && install -d -o $OPENIG_USER $OPENIG_BASE \
    && chown -R $OPENIG_USER:root $CATALINA_HOME \
    && chgrp -R 0 $CATALINA_HOME \
    && chmod -R g=u $CATALINA_HOME \
    && chgrp -R 0 $OPENIG_BASE \
    && chmod -R g=u $OPENIG_BASE \
    && apt-get remove -y --purge unzip && rm -rf /var/lib/apt/lists/* \
    && rm -f /usr/bin/pebble \
    && sed -i '/<\/Host>/i \ \ \ \ <Valve className=\"org.apache.catalina.valves.RemoteIpValve\" httpServerPort=\"8080\" httpsServerPort=\"443\" />' $CATALINA_HOME/conf/server.xml

USER $OPENIG_USER

EXPOSE 8080/tcp

HEALTHCHECK --interval=30s --timeout=30s --start-period=1s --retries=3 CMD curl -v -L --fail http://localhost:8080/openig/ || exit 1
 
CMD ["/usr/local/tomcat/bin/catalina.sh", "run"]