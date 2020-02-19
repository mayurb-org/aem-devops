# ensure DEPLOY_ARTIFACT is defined
if [ "$DEPLOY_ARTIFACT" == "" ]; then DEPLOY_ARTIFACT=brcom-ui; fi;

# download latest artifact
mvn org.apache.maven.plugins:maven-dependency-plugin:2.8:get -U -Ddest=. -DgroupId=com.baskinrobbins.braem -DartifactId=$DEPLOY_ARTIFACT -Dversion=$DEPLOY_VERSION -Dpackaging=zip -Dtransitive=false

# upload and deploy package to CQ
curl -u admin:password -F file=@$DEPLOY_ARTIFACT-$DEPLOY_VERSION.zip -F force=true -F install=true http://<PUB_IP>:4503/crx/packmgr/service.jsp