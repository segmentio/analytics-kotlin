#!/usr/bin/env bash
set -euo pipefail

# Manual release script for Android/Kotlin SDKs.
# Resolves dependencies from internal Artifactory (curated inputs), builds,
# signs with GPG, and publishes to Maven Central via the nexus-publish plugin.
#
# Usage:
#   export SIGNING_KEY_ID=<8-char GPG key ID>
#   export SIGNING_KEY_PASSWORD=<GPG key passphrase>
#   export SIGNING_PRIVATE_KEY_BASE64=<base64-encoded ASCII-armored private key>
#   export ORG_GRADLE_PROJECT_sonatypeUsername=<Central Portal token username>
#   export ORG_GRADLE_PROJECT_sonatypePassword=<Central Portal token password>
#   ./scripts/release.sh

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

# --- Preflight checks ---

echo "==> Preflight checks"

missing=()
[[ -z "${SIGNING_KEY_ID:-}" ]] && missing+=("SIGNING_KEY_ID")
[[ -z "${SIGNING_KEY_PASSWORD:-}" ]] && missing+=("SIGNING_KEY_PASSWORD")
[[ -z "${SIGNING_PRIVATE_KEY_BASE64:-}" ]] && missing+=("SIGNING_PRIVATE_KEY_BASE64")
[[ -z "${ORG_GRADLE_PROJECT_sonatypeUsername:-}" ]] && missing+=("ORG_GRADLE_PROJECT_sonatypeUsername")
[[ -z "${ORG_GRADLE_PROJECT_sonatypePassword:-}" ]] && missing+=("ORG_GRADLE_PROJECT_sonatypePassword")

if [[ ${#missing[@]} -gt 0 ]]; then
  echo "ERROR: Missing required environment variables:"
  printf '  - %s\n' "${missing[@]}"
  exit 1
fi

if [[ ! -f "gradle.properties" ]]; then
  echo "ERROR: gradle.properties not found. Run from project root."
  exit 1
fi

if [[ ! -f "init.gradle" ]]; then
  echo "ERROR: init.gradle not found. See docs/manual-release-guide.md for setup."
  exit 1
fi

# --- Extract project metadata ---

VERSION=$(grep "^VERSION_NAME=" gradle.properties | cut -d= -f2)
GROUP=$(grep "^GROUP=" gradle.properties | cut -d= -f2)

if [[ -z "$VERSION" ]]; then
  echo "ERROR: VERSION_NAME not found in gradle.properties"
  exit 1
fi

if [[ -z "$GROUP" ]]; then
  echo "ERROR: GROUP not found in gradle.properties"
  exit 1
fi

echo "  Version: $VERSION"
echo "  Group: $GROUP"
echo ""

# --- Verify Artifactory reachability ---

echo "==> Verifying Artifactory reachability"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "https://maven.artifacts.twilio.com/artifactory/virtual-maven-thirdparty/" 2>&1) || {
  echo "ERROR: curl failed to connect to Artifactory (exit code $?). Are you on the Twilio VPN?"
  exit 1
}
if [[ "$HTTP_CODE" != "200" ]]; then
  echo "ERROR: Cannot reach Artifactory (HTTP $HTTP_CODE). Are you on the Twilio network?"
  exit 1
fi
echo "  Artifactory: OK"
echo ""

# --- Confirm before proceeding ---

read -rp "Publish $GROUP:$VERSION to Maven Central? [y/N] " confirm
if [[ "$confirm" != "y" && "$confirm" != "Y" ]]; then
  echo "Aborted."
  exit 0
fi

# --- Build, sign, publish, and release ---

echo ""
echo "==> Building, signing, and publishing to Maven Central"
echo "    (dependencies resolved from internal Artifactory)"
echo ""

./gradlew --init-script init.gradle \
  clean build publish publishToSonatype closeAndReleaseSonatypeStagingRepository \
  -Prelease -x test

echo ""
echo "==> Release complete!"
echo ""
echo "Next steps:"
echo "  git tag $VERSION"
echo "  git push origin $VERSION"

