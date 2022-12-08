FROM tomcat:9-jre17

MAINTAINER Open Identity Platform Community <open-identity-platform-openig@googlegroups.com>

ARG VERSION @project_version@


ENV CATALINA_HOME=/usr/local/tomcat
ENV OPENIG_USER="openig"
ENV OPENIG_BASE=/var/openig
ENV PATH=$CATALINA_HOME/bin:$PATH
ENV MAX_MEMORY="1g"
ENV CATALINA_OPTS="-server -XX:+UseG1GC -Xmx$MAX_MEMORY"

WORKDIR $CATALINA_HOME

RUN apt-get update && apt-get install -y wget unzip
RUN wget --quiet https://github.com/OpenIdentityPlatform/OpenIG/releases/download/$VERSION/OpenIG-$VERSION.war
RUN rm -fr $CATALINA_HOME/webapps/*
RUN mv *.war $CATALINA_HOME/webapps/ROOT.war
RUN useradd -m -r -u 1001 -g root $OPENIG_USER
RUN install -d -o $OPENIG_USER $OPENIG_BASE
RUN chown -R $OPENIG_USER:root $CATALINA_HOME
RUN  apt-get remove -y --purge unzip && rm -rf /var/lib/apt/lists/* 

USER $OPENIG_USER

EXPOSE 8080 

HEALTHCHECK --interval=30s --timeout=30s --start-period=1s --retries=3 CMD curl -v -L --fail http://localhost:8080/openig/ || exit 1
 
CMD ["/usr/local/tomcat/bin/catalina.sh", "run"]