#!/usr/bin/env bash
set -euo pipefail

# Sign an unsigned .app or .ipa with the local P12/mobileprovision profile and install directly to a connected iPhone via devicectl.
# Usage:
#   ios-sign-install <path-to-.app-or-.ipa> [--device <UDID>] [--bundle-id <id>] [--name <display-name>] [--out-ipa <path>] [--launch] [--no-install]

DEFAULT_PROFILE="/Users/rinshibuya/Downloads/Erl-Project/P12 Sign/描述文件 2.mobileprovision"
DEFAULT_IDENTITY="420F38DF928050DDB376A08FB13915557B7FAB88"

if [ $# -lt 1 ]; then
  echo "Usage: $(basename "$0") <.app|.ipa> [--device UDID] [--bundle-id ID] [--name NAME] [--profile PATH] [--identity SHA1_OR_NAME] [--out-ipa PATH] [--launch] [--no-install]" >&2
  exit 1
fi

INPUT="$1"
shift

DEVICE=""
BUNDLE_ID=""
APP_DISPLAY_NAME=""
PROFILE="$DEFAULT_PROFILE"
IDENTITY="$DEFAULT_IDENTITY"
OUT_IPA=""
DO_INSTALL=1
DO_LAUNCH=0

while [ $# -gt 0 ]; do
  case "$1" in
    --device) DEVICE="$2"; shift 2 ;;
    --bundle-id) BUNDLE_ID="$2"; shift 2 ;;
    --name) APP_DISPLAY_NAME="$2"; shift 2 ;;
    --profile) PROFILE="$2"; shift 2 ;;
    --identity) IDENTITY="$2"; shift 2 ;;
    --out-ipa) OUT_IPA="$2"; shift 2 ;;
    --launch) DO_LAUNCH=1; shift ;;
    --no-install) DO_INSTALL=0; shift ;;
    *) echo "Unknown option: $1" >&2; exit 1 ;;
  esac
done

[ -e "$INPUT" ] || { echo "Input not found: $INPUT" >&2; exit 1; }
[ -f "$PROFILE" ] || { echo "Provisioning profile not found: $PROFILE" >&2; exit 1; }

WORKDIR="$(mktemp -d /tmp/ios-sign-XXXXXX)"
trap 'rm -rf "$WORKDIR"' EXIT

mkdir -p "$WORKDIR/Payload"
if [[ "$INPUT" == *.ipa ]]; then
  unzip -q "$INPUT" -d "$WORKDIR"
  APP_DIR="$(find "$WORKDIR/Payload" -maxdepth 1 -name "*.app" | head -n 1)"
elif [[ "$INPUT" == *.app && -d "$INPUT" ]]; then
  cp -R "$INPUT" "$WORKDIR/Payload/"
  APP_DIR="$WORKDIR/Payload/$(basename "$INPUT")"
else
  echo "Input must be a .app bundle or .ipa archive: $INPUT" >&2
  exit 1
fi

[ -d "$APP_DIR" ] || { echo "No .app bundle found inside $INPUT" >&2; exit 1; }

# Update CFBundleIdentifier / CFBundleDisplayName if requested
if [ -n "$BUNDLE_ID" ]; then
  /usr/libexec/PlistBuddy -c "Set :CFBundleIdentifier $BUNDLE_ID" "$APP_DIR/Info.plist"
fi
if [ -n "$APP_DISPLAY_NAME" ]; then
  /usr/libexec/PlistBuddy -c "Set :CFBundleDisplayName $APP_DISPLAY_NAME" "$APP_DIR/Info.plist" 2>/dev/null \
    || /usr/libexec/PlistBuddy -c "Add :CFBundleDisplayName string $APP_DISPLAY_NAME" "$APP_DIR/Info.plist"
fi
FINAL_BUNDLE_ID="$(/usr/libexec/PlistBuddy -c "Print :CFBundleIdentifier" "$APP_DIR/Info.plist")"
FINAL_VERSION="$(/usr/libexec/PlistBuddy -c "Print :CFBundleShortVersionString" "$APP_DIR/Info.plist" 2>/dev/null || echo "")"
FINAL_BUILD="$(/usr/libexec/PlistBuddy -c "Print :CFBundleVersion" "$APP_DIR/Info.plist" 2>/dev/null || echo "")"

# Extract entitlements from provisioning profile
security cms -D -i "$PROFILE" > "$WORKDIR/profile.plist"
/usr/libexec/PlistBuddy -x -c "Print :Entitlements" "$WORKDIR/profile.plist" > "$WORKDIR/entitlements.plist"

# Embed provisioning profile and clear quarantine/xattrs
cp "$PROFILE" "$APP_DIR/embedded.mobileprovision"
xattr -cr "$APP_DIR"

# Sign nested frameworks / dylibs / plugins first (if any)
if [ -d "$APP_DIR/Frameworks" ]; then
  find "$APP_DIR/Frameworks" \( -name "*.dylib" -o -name "*.framework" \) -print0 | while IFS= read -r -d '' item; do
    codesign --force --sign "$IDENTITY" --timestamp=none "$item"
  done
fi
if [ -d "$APP_DIR/PlugIns" ]; then
  find "$APP_DIR/PlugIns" -maxdepth 1 -name "*.appex" -print0 | while IFS= read -r -d '' item; do
    codesign --force --sign "$IDENTITY" --entitlements "$WORKDIR/entitlements.plist" --timestamp=none "$item"
  done
fi

# Sign main .app bundle
codesign --force --sign "$IDENTITY" --entitlements "$WORKDIR/entitlements.plist" --timestamp=none "$APP_DIR"
codesign --verify --deep --strict "$APP_DIR"
echo "Signed $(basename "$APP_DIR") ($FINAL_BUNDLE_ID $FINAL_VERSION/$FINAL_BUILD)"

if [ -n "$OUT_IPA" ]; then
  mkdir -p "$(dirname "$OUT_IPA")"
  ABS_OUT="$(cd "$(dirname "$OUT_IPA")" && pwd)/$(basename "$OUT_IPA")"
  (cd "$WORKDIR" && zip -qry "$ABS_OUT" Payload)
  echo "Wrote signed IPA: $ABS_OUT"
fi

if [ "$DO_INSTALL" -eq 1 ]; then
  if [ -z "$DEVICE" ]; then
    DEVICE="$(xcrun devicectl list devices 2>/dev/null | awk '
      /iPhone/ && /connected/ { for (i=1;i<=NF;i++) if ($i ~ /^[0-9A-F]{8}-[0-9A-F]{16}$/) { print $i; exit } }
    ')"
    if [ -z "$DEVICE" ]; then
      DEVICE="$(xcrun devicectl list devices 2>/dev/null | awk '
        /iPhone/ && /available/ { for (i=1;i<=NF;i++) if ($i ~ /^[0-9A-F]{8}-[0-9A-F]{16}$/) { print $i; exit } }
      ')"
    fi
  fi
  [ -n "$DEVICE" ] || { echo "No connected/available iPhone found. Pass --device <UDID>." >&2; exit 1; }
  echo "Installing to device $DEVICE ..."
  xcrun devicectl device install app --device "$DEVICE" "$APP_DIR"
  if [ "$DO_LAUNCH" -eq 1 ]; then
    echo "Launching $FINAL_BUNDLE_ID on $DEVICE ..."
    xcrun devicectl device process launch --device "$DEVICE" "$FINAL_BUNDLE_ID"
  fi
fi
