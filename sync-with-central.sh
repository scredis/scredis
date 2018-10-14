#!/bin/bash

VERSION=`./get-version.sh`
echo "Syncing version $VERSION"
curl --globoff -X POST -u kpbochenek:$BINTRAY_API_KEY https://api.bintray.com/maven_central_sync/scredis/maven/scredis/versions/$VERSION
