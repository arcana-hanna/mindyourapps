#!/usr/bin/env bash
# Creates a permanent signing key for Mind Your Apps releases.
# Needs a JDK (for keytool) and openssl. GitHub Codespaces has both.
#
#   bash scripts/make-keystore.sh
#
# Then add the four printed values as repository secrets:
#   Settings > Secrets and variables > Actions > New repository secret
set -euo pipefail

ALIAS="mindyourapps"
FILE="mindyourapps-release.jks"

if [ -e "$FILE" ]; then
  echo "$FILE already exists. Refusing to overwrite it." >&2
  exit 1
fi

PASS="$(openssl rand -base64 24 | tr -dc 'A-Za-z0-9' | cut -c1-24)"

keytool -genkeypair \
  -keystore "$FILE" \
  -alias "$ALIAS" \
  -keyalg RSA -keysize 4096 -validity 36500 \
  -storepass "$PASS" -keypass "$PASS" \
  -dname "CN=Mind Your Apps"

# base64 flags differ between Linux and macOS
if base64 --help 2>&1 | grep -q -- '-w'; then
  B64="$(base64 -w0 "$FILE")"
else
  B64="$(base64 < "$FILE" | tr -d '\n')"
fi

cat <<EOF

Done. Back up $FILE somewhere safe (a password manager works) and never commit it.
If you lose it, future releases can no longer update installs of earlier ones.

Add these four repository secrets:

  KEY_ALIAS          $ALIAS
  KEYSTORE_PASSWORD  $PASS
  KEY_PASSWORD       $PASS
  KEYSTORE_BASE64    (the long value below)

$B64

EOF
