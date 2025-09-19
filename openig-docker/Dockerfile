FROM tomcat:11-jre21

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

RUN apt-get update && apt-get install -y curl unzip \
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
    && sed -i '/<\/Host>/i \ \ \ \ <Valve className=\"org.apache.catalina.valves.RemoteIpValve\" httpServerPort=\"8080\" httpsServerPort=\"443\" />' $CATALINA_HOME/conf/server.xml

USER $OPENIG_USER

EXPOSE 8080/tcp

HEALTHCHECK --interval=30s --timeout=30s --start-period=1s --retries=3 CMD curl -v -L --fail http://localhost:8080/openig/ || exit 1
 
CMD ["/usr/local/tomcat/bin/catalina.sh", "run"]