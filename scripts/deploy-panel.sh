#!/usr/bin/env bash
# Build the owner web panel and publish it on panel.noobatyar.ir.
#
# The panel is static files (Kotlin/Wasm) that nginx serves from
# backend/panel/ on the server — see the panel server block in
# backend/nginx/nginx.conf. That folder is not in git, so a backend deploy
# never changes the panel; this script is the panel's deploy.
#
#   DEPLOY_HOST=194.146.69.137 scripts/deploy-panel.sh
#
# Key-based SSH only (see scripts/remote.sh). Run from the repo root, with
# no preview/dev web server running (it holds the Gradle lock).

set -euo pipefail

HOST="${DEPLOY_HOST:?set DEPLOY_HOST to the production server}"
JAVA="${JAVA:-/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java}"
DIST="mobile_owner/composeApp/build/dist/wasmJs/productionExecutable"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo "› building the production bundle"
"$JAVA" -classpath mobile_owner/gradle/wrapper/gradle-wrapper.jar \
  org.gradle.wrapper.GradleWrapperMain -p mobile_owner \
  :composeApp:wasmJsBrowserDistribution --console=plain -q

echo "› packing (no source map, a .gz beside each file for gzip_static)"
mkdir "$WORK/panel"
cp -R "$DIST/." "$WORK/panel/"
rm -f "$WORK"/panel/*.map
find "$WORK/panel" -type f \( -name '*.wasm' -o -name '*.js' -o -name '*.html' \
  -o -name '*.webmanifest' -o -name '*.json' -o -name '*.ttf' -o -name '*.otf' \
  -o -name '*.svg' -o -name '*.xml' -o -name '*.txt' -o -name '*.cvr' \) \
  -exec gzip -k -9 {} \;
# COPYFILE_DISABLE: no macOS xattr records in the archive (GNU tar on the
# server prints a warning for every one of them).
COPYFILE_DISABLE=1 tar czf "$WORK/panel.tgz" -C "$WORK" panel

echo "› uploading $(du -h "$WORK/panel.tgz" | cut -f1)"
scp -o BatchMode=yes -q "$WORK/panel.tgz" "root@$HOST:/root/panel.tgz"

# Synced into the live folder rather than swapped in by renaming it: nginx
# bind-mounts that exact directory, and a renamed one would stay mounted in
# its place. The new hashed .wasm files go in first, so a page that loads
# the new composeApp.js mid-deploy never asks for a wasm that isn't there
# yet; the old ones are deleted last. The previous bundle stays in
# panel.prev for a rollback (rsync -a --delete panel.prev/ panel/).
ssh -o BatchMode=yes "root@$HOST" 'set -e
  cd /root/app/backend
  rm -rf panel.new && mkdir panel.new
  tar xzf /root/panel.tgz -C panel.new --strip-components=1
  mkdir -p panel
  rm -rf panel.prev && cp -a panel panel.prev
  cp -a panel.new/*.wasm panel.new/*.wasm.gz panel/
  rsync -a --delete-after panel.new/ panel/
  rm -rf panel.new
  echo "  live: $(ls panel/*.wasm | wc -l) wasm files, $(du -sh panel | cut -f1)"'

echo "› https://panel.noobatyar.ir/ → $(curl -s -o /dev/null -w '%{http_code}' https://panel.noobatyar.ir/)"
