#!/bin/sh
set -eu

PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
BUILD_DIR="$PROJECT_DIR/build"
DIST_DIR="$PROJECT_DIR/dist"
SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
BUILD_TOOLS_VERSION="${BUILD_TOOLS_VERSION:-36.1.0}"
XPOSED_API="${XPOSED_API_JAR:-}"
KEYSTORE="${SIGNING_KEYSTORE:-$PROJECT_DIR/debug.keystore}"
KEYSTORE_ALIAS="${SIGNING_ALIAS:-androiddebugkey}"
KEYSTORE_PASSWORD="${SIGNING_STORE_PASSWORD:-android}"
KEY_PASSWORD="${SIGNING_KEY_PASSWORD:-android}"

if [ -z "$SDK_DIR" ]; then
    echo "Set ANDROID_SDK_ROOT or ANDROID_HOME." >&2
    exit 1
fi

if [ -z "$XPOSED_API" ]; then
    echo "Set XPOSED_API_JAR to a legacy Xposed API JAR." >&2
    exit 1
fi

BUILD_TOOLS="$SDK_DIR/build-tools/$BUILD_TOOLS_VERSION"
ANDROID_JAR="$SDK_DIR/platforms/android-36/android.jar"
OUTPUT_APK="$DIST_DIR/OneUI-8.5-AI-Notifications-v1.5.apk"

for required_file in \
    "$BUILD_TOOLS/aapt2" \
    "$BUILD_TOOLS/d8" \
    "$BUILD_TOOLS/zipalign" \
    "$BUILD_TOOLS/apksigner" \
    "$ANDROID_JAR" \
    "$XPOSED_API"; do
    if [ ! -e "$required_file" ]; then
        echo "Missing required file: $required_file" >&2
        exit 1
    fi
done

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/compiled-res" "$BUILD_DIR/generated" \
    "$BUILD_DIR/classes" "$BUILD_DIR/dex" "$DIST_DIR"

"$BUILD_TOOLS/aapt2" compile --dir "$PROJECT_DIR/res" \
    -o "$BUILD_DIR/compiled-res/resources.zip"

"$BUILD_TOOLS/aapt2" link \
    -o "$BUILD_DIR/resources.apk" \
    -I "$ANDROID_JAR" \
    --manifest "$PROJECT_DIR/AndroidManifest.xml" \
    --java "$BUILD_DIR/generated" \
    -A "$PROJECT_DIR/assets" \
    --min-sdk-version 34 \
    --target-sdk-version 36 \
    --version-code 6 \
    --version-name 1.5 \
    "$BUILD_DIR/compiled-res/resources.zip"

find "$PROJECT_DIR/src" "$BUILD_DIR/generated" -name '*.java' -print \
    > "$BUILD_DIR/java-sources.list"

javac \
    -source 8 \
    -target 8 \
    -encoding UTF-8 \
    -classpath "$ANDROID_JAR:$XPOSED_API" \
    -d "$BUILD_DIR/classes" \
    @"$BUILD_DIR/java-sources.list"

find "$BUILD_DIR/classes" -name '*.class' -print \
    > "$BUILD_DIR/class-files.list"

"$BUILD_TOOLS/d8" \
    --min-api 34 \
    --lib "$ANDROID_JAR" \
    --output "$BUILD_DIR/dex" \
    @"$BUILD_DIR/class-files.list"

cp "$BUILD_DIR/resources.apk" "$BUILD_DIR/unsigned.apk"
(
    cd "$BUILD_DIR/dex"
    zip -q "$BUILD_DIR/unsigned.apk" classes.dex
)

"$BUILD_TOOLS/zipalign" -f 4 \
    "$BUILD_DIR/unsigned.apk" "$BUILD_DIR/aligned.apk"

if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair \
        -keystore "$KEYSTORE" \
        -storepass "$KEYSTORE_PASSWORD" \
        -keypass "$KEY_PASSWORD" \
        -alias "$KEYSTORE_ALIAS" \
        -dname "CN=OneUI 8.5 AI Notifications,O=Local Build,C=US" \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -noprompt
fi

"$BUILD_TOOLS/apksigner" sign \
    --ks "$KEYSTORE" \
    --ks-key-alias "$KEYSTORE_ALIAS" \
    --ks-pass "pass:$KEYSTORE_PASSWORD" \
    --key-pass "pass:$KEY_PASSWORD" \
    --out "$OUTPUT_APK" \
    "$BUILD_DIR/aligned.apk"

"$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$OUTPUT_APK"
echo "$OUTPUT_APK"
