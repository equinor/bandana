#!/bin/bash

set -e

SCRIPT_LOCATION=$(dirname $0)
BASE_LOCATION=$(dirname $SCRIPT_LOCATION)
BUILD_LOCATION="$BASE_LOCATION/build"
BANDANA_LOCATION="$BASE_LOCATION/.."
FUSEKI_VER="4.9.0"
FUSEKI_LOCATION="$BUILD_LOCATION/jena-fuseki-docker-$FUSEKI_VER"
ZIP_LOCATION="$FUSEKI_LOCATION.zip"
IMAGE_URL="https://repo1.maven.org/maven2/org/apache/jena/jena-fuseki-docker/$FUSEKI_VER/jena-fuseki-docker-$FUSEKI_VER.zip"

#checks
commands=("docker" "unzip")
for cmd in ${commands[@]}; do
    if ! command -v $cmd &> /dev/null; then
        >&2 echo "$cmd must be installed"
        exit 1
    fi
done

#clean
[ -d "$BUILD_LOCATION" ] && rm -r $BUILD_LOCATION
mkdir -p $BUILD_LOCATION

#download
echo "curl $IMAGE_URL -o $ZIP_LOCATION"
curl $IMAGE_URL -o $ZIP_LOCATION
echo $(cat $(basename "$ZIP_LOCATION.md5")) "$ZIP_LOCATION" | md5sum -c
unzip $ZIP_LOCATION -d $BUILD_LOCATION

#build unmodified fuseki
docker build --force-rm --build-arg JENA_VERSION=$FUSEKI_VER -t rawfuseki $FUSEKI_LOCATION

#build bandanafuseki
$BANDANA_LOCATION/gradlew -p $BANDANA_LOCATION shadowjar
cp $BANDANA_LOCATION/bandanaModule/build/libs/bandanaModule-all.jar $BASE_LOCATION/bandana.jar
docker build --force-rm --build-arg JENA_VERSION=$FUSEKI_VER -t bandanafuseki -f $BASE_LOCATION/bandana-Dockerfile $BASE_LOCATION



