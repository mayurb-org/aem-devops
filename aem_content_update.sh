#  current time
CURRENT_TIME=`date +%Y-%m-%dT%T.%N%:z`
CURRENT_EPOCH=`date +%s`

# Other
AUTHOR_URL="http://author-url"
SITE_ID="TEST"
WEBSITE="https://site.com"
AKAMAI_CREDS="username:password"

IFS=$'
'

mkdir -p ../backups
if [ ! -h backups ]; then ln -s ../backups backups; fi;
mkdir -p temp
cd temp

# download blank template and appropriate replication agent packages
curl -O $AUTHOR_URL/MISC-AEM/BLANK_TEMPLATE-1.0.zip
curl -O $AUTHOR_URL/MISC-AEM/$SITE_ID-replication-agents-ENABLED-2.0.zip

# make package for this build / set of files
unzip BLANK_TEMPLATE-1.0.zip -d $SITE_ID-production-window-update-$BUILD_NUMBER-backup
cd $SITE_ID-production-window-update-$BUILD_NUMBER-backup

# set up the filter.xml file
echo "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<workspaceFilter version=\"1.0\">" > META-INF/vault/filter.xml
for i in $(echo -e "$DEACTIVATE_URLS"); do
  echo "    <filter root=\"$i\" />" >> META-INF/vault/filter.xml
done
for i in $(echo -e "$ACTIVATE_URLS"); do
  echo "    <filter root=\"$i\" />" >> META-INF/vault/filter.xml
done
echo "</workspaceFilter>" >> META-INF/vault/filter.xml

# change the name, group, and date of the package
sed -i "s/BLANK_TEMPLATE/$SITE_ID-production-window-update-$BUILD_NUMBER-backup/" META-INF/vault/properties.xml
sed -i "s/my_packages/$SITE_ID/" META-INF/vault/properties.xml
sed -i "s/[0-9]\{4\}-[0-9]\{2\}.*[0-9]\{2\}:[0-9]\{2\}/$CURRENT_TIME/" META-INF/vault/properties.xml

sed -i "s/BLANK_TEMPLATE/$SITE_ID-production-window-update-$BUILD_NUMBER-backup/" META-INF/vault/definition/.content.xml
sed -i "s/my_packages/$SITE_ID/" META-INF/vault/definition/.content.xml
sed -i "s/[0-9]\{4\}-[0-9]\{2\}.*[0-9]\{2\}:[0-9]\{2\}/$CURRENT_TIME/" META-INF/vault/definition/.content.xml

# zip up the package
zip -r ../$SITE_ID-production-window-update-$BUILD_NUMBER-backup.zip .
cd ../..


# upload backup package and trigger a build
curl -u 'admin:admin' -F force=true -F install=false -F file=@temp/$SITE_ID-production-window-update-$BUILD_NUMBER-backup.zip http://<PUBIP>:4503/crx/packmgr/service.jsp
curl -u 'admin:admin' -X POST "http://<PUBIP>:4503/crx/packmgr/service/.json/etc/packages/$SITE_ID/$SITE_ID-production-window-update-$BUILD_NUMBER-backup-1.0.zip?cmd=build"

# download that backup package
curl -u 'admin:admin' -C - "http://<PUBIP>:4503/etc/packages/$SITE_ID/$SITE_ID-production-window-update-$BUILD_NUMBER-backup-1.0.zip" > backups/$SITE_ID-production-window-update-$BUILD_NUMBER-backup-pub1.zip



# upload backup package and trigger a build
curl -u 'admin:admin' -F force=true -F install=false -F file=@temp/$SITE_ID-production-window-update-$BUILD_NUMBER-backup.zip http://<PUBIP>:4503/crx/packmgr/service.jsp
curl -u 'admin:admin' -X POST "http://<PUBIP>:4503/crx/packmgr/service/.json/etc/packages/$SITE_ID/$SITE_ID-production-window-update-$BUILD_NUMBER-backup-1.0.zip?cmd=build"

# download that backup package
curl -u 'admin:admin' -C - "http://<PUBIP>:4503/etc/packages/Baskin%20Robbins/$SITE_ID-production-window-update-$BUILD_NUMBER-backup-1.0.zip" > backups/$SITE_ID-production-window-update-$BUILD_NUMBER-backup-pub2.zip



if [ "$DEPLOY_PACKAGE" != "" ]; then
  cd temp
  # download package
  curl -O "$AUTHOR_URL/content/$DEPLOY_PACKAGE.zip"


  if [ "$DEPLOY_PACKAGE" != "" ]; then
    # upload and deploy package to AEM pub0
    curl -u 'admin:admin' -F file=@$DEPLOY_PACKAGE.zip -F force=true -F install=true http://<PUBIP>:4503/crx/packmgr/service.jsp
  fi


  if [ "$DEPLOY_PACKAGE" != "" ]; then
    # upload and deploy package to AEM pub1
    curl -u 'admin:admin' -F file=@$DEPLOY_PACKAGE.zip -F force=true -F install=true http://10.70.13.170:4503/crx/packmgr/service.jsp
  fi

  cd ..
fi

# disable all replication agents and enable only the ones we want
curl -u 'admin:admin' -F file=@temp/$SITE_ID-PRODUCTION-replication-agents-ENABLED-2.0.zip -F force=true -F install=true http://<AUTH_IP>:4502/crx/packmgr/service.jsp
sleep 10

# start purge request body
echo '{ "objects" : [' > ccu_purge.txt
ISFIRST=1

for i in $(echo -e "$ACTIVATE_URLS"); do
  case "$i" in
    /content/baskinrobbins/en | /content/baskinrobbins | /content)
      echo  Refusing to activate root URL: $i
    ;;
    */jcr:content* | /etc/designs/*.* | *.*)
      echo ACTIVATING $i
      curl -u 'admin:admin' -X POST -F path=$i -F cmd=activate http://<AUTH_IP>:4502/bin/replicate.json
    ;;
    *)
      echo TREE ACTIVATING $i
      curl -u 'admin:admin' -F cmd=activate -F path=$i http://<AUTH_IP>:4502/etc/replication/treeactivation.html
    ;;
  esac;
  if [[ "$i" == "/content"* ]]; then
    # NOTE: the dam paths will add a * (but the CCU doesn't support it)
    CCU_PATH=`echo $i | sed 's/\/jcr:content.*/.html/' | sed 's/\(.*content\/dam.*\)/\1*/' | sed 's/\(.*content\/baskinrobbins.*\)/\1.html/'`
    if [ "$ISFIRST" == "1" ]; then
      ISFIRST=0
    else
      echo -e ',
' >> ccu_purge.txt
    fi
    echo '"https://site.com'$CCU_PATH'"' >> ccu_purge.txt
  fi
done;

for i in $(echo -e "$DEACTIVATE_URLS"); do
  case "$i" in
    /content/baskinrobbins/en | /content/baskinrobbins | /content)
      echo Cowardly refusing to deactivate root URL: $i
    ;;
    *)
      echo DEACTIVATING $i
      curl -u 'admin:admin' -X POST -F path=$i -F cmd=deactivate http://10.64.193.236:4504/bin/replicate.json
    ;;
  esac;
  if [[ "$i" == "/content"* ]]; then
    # NOTE: the dam paths will add a * (but the CCU doesn't support it)
    CCU_PATH=`echo $i | sed 's/\/jcr:content.*/.html/' | sed 's/\(.*content\/dam.*\)/\1*/' | sed 's/\(.*content\/baskinrobbins.*\)/\1.html/'`
    if [ "$ISFIRST" == "1" ]; then
      ISFIRST=0
    else
      echo -e ',
' >> ccu_purge.txt
    fi
    echo '"$WEBSITE'$CCU_PATH'"' >> ccu_purge.txt
  fi
done;

# letting the agents run
sleep 60

# end purge request body
echo ']}' >> ccu_purge.txt

if [ "$WEBSITE" != "" ] && [ "$ISFIRST" != "1" ]; then
  # trigger Akamai cache clearing
  curl -v -X POST https://api.ccu.akamai.com/ccu/v2/queues/default -H "Content-Type:application/json" -d @ccu_purge.txt -u "$AKAMAI_CREDS"
fi

# disable all replication agents if production
if [ "production" == "production" ]; then
  sleep 30
  curl -u 'admin:admin' -F file=@temp/$SITE_ID-deactivate-ALL-replication-agents-2.0.zip -F force=true -F install=true http://<AUTH_IP>:4502/crx/packmgr/service.jsp
fi
