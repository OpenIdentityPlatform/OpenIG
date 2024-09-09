FROM tomcat:9-jre21

MAINTAINER Open Identity Platform Community <open-identity-platform-openig@googlegroups.com>

ARG VERSION @project_version@

ENV CATALINA_HOME=/usr/local/tomcat
ENV OPENIG_USER="openig"
ENV OPENIG_BASE=/var/openig
ENV PATH=$CATALINA_HOME/bin:$PATH
ENV MEMORY="-server -XX:+UseG1GC -XX:+UseContainerSupport"
ENV CATALINA_OPTS="$MEMORY"

WORKDIR $CATALINA_HOME

RUN apt-get update && apt-get install -y wget unzip \
    && wget --quiet https://github.com/OpenIdentityPlatform/OpenIG/releases/download/$VERSION/OpenIG-$VERSION.war \
    && rm -fr $CATALINA_HOME/webapps/* \
    && mv *.war $CATALINA_HOME/webapps/ROOT.war \
    && useradd -m -r -u 1001 -g root $OPENIG_USER \
    && install -d -o $OPENIG_USER $OPENIG_BASE \
    && chown -R $OPENIG_USER:root $CATALINA_HOME \
    &&  apt-get remove -y --purge unzip && rm -rf /var/lib/apt/lists/*

USER $OPENIG_USER

EXPOSE 8080 

HEALTHCHECK --interval=30s --timeout=30s --start-period=1s --retries=3 CMD curl -v -L --fail http://localhost:8080/openig/ || exit 1
 
CMD ["/usr/local/tomcat/bin/catalina.sh", "run"]