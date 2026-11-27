#!/usr/bin/env bash
# Install the pinned Spring Boot Web and authentication adapters without relying on a mutable snapshot.
set -euo pipefail

revision=22c433aa155b276b0bf3ede2515edf8c6b3c902e
root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
source_dir=$(mktemp -d "${TMPDIR:-/tmp}/ddd4j-boot-web.XXXXXXXX")
trap 'rm -rf -- "$source_dir"' EXIT

# The adapters depend on this reactor's DDD4J Web contracts and BOM.
(cd "$root" && bash mvnw -B --no-transfer-progress \
  -pl :ddd4j-bom,:ddd4j-auth-spring,:ddd4j-auth-satoken,:ddd4j-auth-shiro,:ddd4j-runtime-spring,:ddd4j-web-webmvc,:ddd4j-web-webflux -am -DskipTests install)

git -C "$source_dir" init --quiet
git -C "$source_dir" fetch --quiet --depth 1 https://github.com/ddd-4-java/ddd4j-boot.git "$revision"
git -C "$source_dir" checkout --quiet --detach FETCH_HEAD
test "$(git -C "$source_dir" rev-parse HEAD)" = "$revision"

# Supply missing Javadoc tags in the pinned sources so doclint stays enabled.
git -C "$source_dir" apply "$root/scripts/patches/boot-2.7-javadoc.diff"

# Use the consuming JDK 8 line's Jackson family when building the adapters.
jackson_version=$(python3 - "$root/ddd4j-dependencies/pom.xml" <<'PYTHON'
import sys
import xml.etree.ElementTree as ET
print(ET.parse(sys.argv[1]).find("{*}properties/{*}jackson.version").text)
PYTHON
)

# Build the pinned adapters and their parents in dependency order. A recursive
# reactor also validates unrelated upstream sample POMs before selecting -pl.
(
  cd "$source_dir"
  for module in . ddd4j-boot-dependencies ddd4j-boot-core ddd4j-boot-web \
    ddd4j-boot-web/ddd4j-boot-web-webmvc ddd4j-boot-web/ddd4j-boot-web-webflux \
    ddd4j-boot-auth ddd4j-boot-auth/ddd4j-boot-auth-satoken ddd4j-boot-auth/ddd4j-boot-auth-shiro; do
    bash mvnw -B --no-transfer-progress -N -f "$module/pom.xml" \
      -Djackson.version="$jackson_version" -Djackson-bom.version="$jackson_version" -DskipTests install
  done
)
