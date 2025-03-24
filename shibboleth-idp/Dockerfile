FROM jetty:11.0.20-jre17

# Download Logback dependencies
RUN java -jar "$JETTY_HOME/start.jar" --add-module=logging-logback --approve-all-licenses

# Shibboleth IdP version to install, see https://shibboleth.net/downloads/identity-provider/
ENV IDP_VERSION=5.1.0

##########################
# Install Shibboleth IdP #
##########################

# Since the jetty docker container runs as non-root, we install the IdP into $JETTY_BASE instead of /opt
RUN set -ex \
 ; cd "$JETTY_BASE" \
 ; wget -nv -O idp.tar.gz "https://shibboleth.net/downloads/identity-provider/$IDP_VERSION/shibboleth-identity-provider-$IDP_VERSION.tar.gz" \
 ; mkdir shibboleth-install \
 ; tar -xzf idp.tar.gz -C shibboleth-install --strip-components=1 \
 ; { \
    echo "idp.target.dir=$JETTY_BASE/shibboleth-idp" ;\
    echo 'idp.host.name=localhost' ;\
    echo 'idp.keystore.password=changeit' ;\
    echo 'idp.sealer.password=changeit' ;\
    echo 'idp.entityID=http://localhost:8080/idp/shibboleth' ;\
    echo 'idp.scope=local' ;\
   } > install.properties \
 ; shibboleth-install/bin/install.sh --propertyFile "$JETTY_BASE/install.properties" \
 ; rm -r idp.tar.gz shibboleth-install

# Install JavaScript plugin
COPY --chown=jetty:jetty /shibboleth-idp/credentials/net.shibboleth.idp.plugin.nashorn/truststore.asc  $JETTY_BASE/shibboleth-idp/credentials/net.shibboleth.idp.plugin.nashorn/truststore.asc
RUN set -ex \
 ; cd "$JETTY_BASE" \
 ; shibboleth-idp/bin/plugin.sh --noPrompt -I net.shibboleth.idp.plugin.nashorn

# Configuration files for Jetty are published in the separate java-idp-jetty-base project.
# See https://shibboleth.atlassian.net/wiki/spaces/IDP5/pages/3199500883/Jetty11
# Specify the git commit, see https://git.shibboleth.net/view/?p=java-idp-jetty-base.git;a=shortlog;h=refs/heads/11
ENV IDP_JETTY_BASE_COMMIT=8a600d12bdb68f40731acd9c57b126debefd842f
RUN set -ex \
 ; cd "$JETTY_BASE" \
 # download and extract the additional files
 ; wget -nv -O idp-jetty-base.tar.gz "https://git.shibboleth.net/view/?p=java-idp-jetty-base.git;a=snapshot;h=$IDP_JETTY_BASE_COMMIT;sf=tgz" \
 ; mkdir idp-jetty-base \
 ; tar -xzf idp-jetty-base.tar.gz -C idp-jetty-base --strip-components=1 \
 ; cp -rf idp-jetty-base/src/main/resources/jetty-base/. . \
 ; rm -r idp-jetty-base.tar.gz idp-jetty-base


#################
# Configuration #
#################

# Add custom configuration files from Docker context
COPY --chown=jetty:jetty / $JETTY_BASE

RUN set -ex \
 ; cd "$JETTY_BASE" \
 # replace $JETTY_BASE by its actual value \
 ; sed -i "s#\$JETTY_BASE#$JETTY_BASE#" start.d/start.ini \
 # disable https
 ; sed -i 's/^\(https\|ssl\)$/# \1/' modules/idp.mod \
 ; sed -i 's/^\(jetty\.ssl\)/# \1/' start.d/idp.ini \
 # use jetty default port 8080 instead of 80
 ; sed -i 's/^\(jetty\.http\)/# \1/' start.d/idp.ini \
 # fix protocol and port numbers in metadata file
 ; sed -i 's#https\?://localhost\(:[0-9]\+\)\?/#http://localhost:8080/#' shibboleth-idp/metadata/idp-metadata.xml \
 # enable single logout service (first command requires perl because of multiline match)
 ; perl -0777 -p -i -e 's/<!-- *(\s*<SingleLogoutService(?:[^-]*(?:-(?!->))?)*)-->/\1/g' shibboleth-idp/metadata/idp-metadata.xml \
 ; sed -i -E 's/^#?(idp\.logout\.elaboration =).*$/\1 true/' shibboleth-idp/conf/idp.properties \
 # allow access to admin functions
 ; sed -i -E 's/^#?(.*\.accessPolicy =).*$/\1 Public/' shibboleth-idp/conf/admin/admin.properties \
 # enable detailed error messages
 ; sed -i -E 's/^#?(idp\.errors\.detailed =).*$/\1 true/' shibboleth-idp/conf/idp.properties \
 # shibboleth's logback configuration would overwrite the one we ship to jetty
 ; cp resources/logback.xml shibboleth-idp/conf/logback.xml \
 # we do not serve static files and do not provide back-channel support
 ; rm webapps/static.xml etc/idp-backchannel.xml modules/idp-backchannel.mod start.d/idp-backchannel.ini
