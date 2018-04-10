FROM tomcat:7.0-jre8

MAINTAINER Open Identity Platform Community <open-identity-platform-openig@googlegroups.com>

ENV CATALINA_HOME /usr/local/tomcat

ENV OPENIG_BASE /var/openig

ENV PATH $CATALINA_HOME/bin:$PATH

WORKDIR $CATALINA_HOME

ENV VERSION @project_version@

ENV CATALINA_OPTS="-Xmx2048m -server"

RUN apt-get install -y wget unzip

RUN wget --quiet https://github.com/OpenIdentityPlatform/OpenIG/releases/download/$VERSION/OpenIG-$VERSION.war

RUN rm -fr $CATALINA_HOME/webapps/*

RUN mv *.war $CATALINA_HOME/webapps/ROOT.war

CMD ["/usr/local/tomcat/bin/catalina.sh", "run"]