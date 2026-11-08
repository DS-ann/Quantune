#!/usr/bin/env bash
set -euo pipefail

VERSION_FILE="${1:-version.properties}"
EXPECTED_VERSION="${2:-}"

if [[ ! -f "$VERSION_FILE" ]]; then
  echo "Version file not found: $VERSION_FILE" >&2
  exit 1
fi

CURRENT_VERSION="$(sed -n 's/^VERSION_NAME=//p' "$VERSION_FILE" | head -n 1)"
CURRENT_CODE="$(sed -n 's/^VERSION_CODE=//p' "$VERSION_FILE" | head -n 1)"

if [[ -n "$EXPECTED_VERSION" && "$CURRENT_VERSION" != "$EXPECTED_VERSION" ]]; then
  echo "Version changed since the release was built: expected $EXPECTED_VERSION, found $CURRENT_VERSION." >&2
  exit 1
fi

if [[ ! "$CURRENT_VERSION" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]]; then
  echo "VERSION_NAME must use MAJOR.MINOR.PATCH numeric format; found: $CURRENT_VERSION" >&2
  exit 1
fi
if [[ ! "$CURRENT_CODE" =~ ^[1-9][0-9]*$ ]]; then
  echo "VERSION_CODE must be a positive integer; found: $CURRENT_CODE" >&2
  exit 1
fi

IFS='.' read -r MAJOR MINOR PATCH <<< "$CURRENT_VERSION"
NEXT_MINOR=$((MINOR + 1))
NEXT_CODE=$((CURRENT_CODE + 1))
NEXT_VERSION="$MAJOR.$NEXT_MINOR.0"

TEMP_FILE="$(mktemp)"
printf 'VERSION_NAME=%s\nVERSION_CODE=%s\n' "$NEXT_VERSION" "$NEXT_CODE" > "$TEMP_FILE"
mv "$TEMP_FILE" "$VERSION_FILE"
echo "Next Quantune version: $NEXT_VERSION (versionCode $NEXT_CODE)"
