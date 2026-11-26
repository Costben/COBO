#!/usr/bin/env bash
# Publish the local COUI fork (Android + iOS + KMP metadata) into
# ../coui/build/repository/local, where settings.gradle.kts resolves it.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
COUI="$ROOT/coui"
JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"

MODULES=(coui-core coui-ui coui-blur coui-shader coui-squircle coui-icons coui-preference)

TASKS=()
for module in "${MODULES[@]}"; do
  TASKS+=(":$module:publishAndroidPublicationToLocalRepository")
  TASKS+=(":$module:publishIosArm64PublicationToLocalRepository")
  TASKS+=(":$module:publishIosSimulatorArm64PublicationToLocalRepository")
  TASKS+=(":$module:publishKotlinMultiplatformPublicationToLocalRepository")
done

cd "$COUI"
JAVA_HOME="$JAVA_HOME" ./gradlew "${TASKS[@]}" -ProllingSnapshot -x dokkaGenerate

echo
echo "COUI published to $COUI/build/repository/local"
