#!/usr/bin/env bash
# Bare-javac build for voxyserver-worldgen-cap.
# Classpath: MC server jar + fabric-loader + sponge-mixin + the two target mod
# jars (voxyserver + voxyworldgenv2) so javac can resolve the mixin targets.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

VERSION=$(grep '^mod_version=' gradle.properties | cut -d= -f2)
JAR_NAME="voxyserver-worldgen-cap-${VERSION}.jar"
MC_DATA="/home/alper/stack/minecraft/data"

MC_JAR="$MC_DATA/versions/26.1.2/server-26.1.2.jar"
LOADER_JAR="$MC_DATA/libraries/net/fabricmc/fabric-loader/0.19.2/fabric-loader-0.19.2.jar"
SPONGE_JAR=$(ls "$MC_DATA"/libraries/net/fabricmc/sponge-mixin/*/sponge-mixin-*.jar | head -1)
VOXYSERVER_JAR="/home/alper/stack/minecraft/extra-mods/VoxyServer-1.1.6.jar"
VOXYWG_JAR="/home/alper/stack/minecraft/extra-mods/Voxy%20World%20Gen%20V2-26.1.2-2.2.4.jar"
JSPECIFY_JAR=$(ls "$MC_DATA"/libraries/org/jspecify/jspecify/*/jspecify-*.jar | head -1)
DATAFIXER_JAR=$(ls "$MC_DATA"/libraries/com/mojang/datafixerupper/*/datafixerupper-*.jar | head -1)
FASTUTIL_JAR=$(ls "$MC_DATA"/libraries/it/unimi/dsi/fastutil/*/fastutil-*.jar | head -1)

for f in "$MC_JAR" "$LOADER_JAR" "$SPONGE_JAR" "$VOXYSERVER_JAR" "$VOXYWG_JAR" \
         "$JSPECIFY_JAR" "$DATAFIXER_JAR" "$FASTUTIL_JAR"; do
    [[ -f "$f" ]] || { echo "missing $f"; exit 1; }
done

rm -rf .build-tmp build/libs
mkdir -p .build-tmp/cp .build-tmp/out .build-tmp/res build/libs

cp "$SPONGE_JAR" .build-tmp/cp/sponge-mixin.jar
cp "$VOXYSERVER_JAR" .build-tmp/cp/voxyserver.jar
cp "$VOXYWG_JAR" .build-tmp/cp/voxywg.jar
cp "$JSPECIFY_JAR" .build-tmp/cp/jspecify.jar
cp "$DATAFIXER_JAR" .build-tmp/cp/datafixerupper.jar
cp "$FASTUTIL_JAR" .build-tmp/cp/fastutil.jar

cp -r src/main/resources/. .build-tmp/res/
sed -i "s/\${version}/$VERSION/g" .build-tmp/res/fabric.mod.json

docker run --rm \
    -v "$SCRIPT_DIR":/build \
    -v "$MC_JAR":/cp/mc.jar:ro \
    -v "$LOADER_JAR":/cp/loader.jar:ro \
    -w /build \
    -e JAR_NAME="$JAR_NAME" \
    eclipse-temurin:25-jdk \
    bash -euo pipefail -c '
        CP="$(printf "%s:" .build-tmp/cp/*.jar)/cp/mc.jar:/cp/loader.jar"

        javac --release 25 -d .build-tmp/out -cp "$CP" $(find src/main/java -name "*.java")

        jar --create --file "build/libs/$JAR_NAME" -C .build-tmp/out . -C .build-tmp/res .
        echo "built build/libs/$JAR_NAME"
    '

ls -lh "build/libs/$JAR_NAME"
