#!/usr/bin/env bash
# Builds every target one after another (parallel Loom builds can corrupt the shared mapping cache)
# and copies the release jars to dist/.
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p dist
for v in 1.20.1 1.21.1; do
  (cd "$v" && ./gradlew build --console=plain)
done
cp 1.20.1/fabric/build/libs/helldivers_ping-fabric-*+1.20.1.jar \
   1.20.1/forge/build/libs/helldivers_ping-forge-*+1.20.1.jar \
   1.21.1/fabric/build/libs/helldivers_ping-fabric-*+1.21.1.jar \
   1.21.1/neoforge/build/libs/helldivers_ping-neoforge-*+1.21.1.jar dist/
rm -f dist/*-dev-shadow.jar dist/*-sources.jar
ls -1 dist
