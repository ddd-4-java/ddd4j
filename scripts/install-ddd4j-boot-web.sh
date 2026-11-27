#!/usr/bin/env bash
# Install the pinned Spring Boot Web and authentication adapters without relying on a mutable snapshot.
set -euo pipefail

revision=5201dea59e8d0c8691ed22608c543297426db704
root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
source_dir=$(mktemp -d "${TMPDIR:-/tmp}/ddd4j-boot-web.XXXXXXXX")
trap 'rm -rf -- "$source_dir"' EXIT

# The adapters depend on this reactor's DDD4J Web contracts and BOM.
(cd "$root" && bash mvnw -B --no-transfer-progress \
  -pl :ddd4j-bom,:ddd4j-runtime-spring,:ddd4j-web-webmvc,:ddd4j-web-webflux -am -DskipTests install)

git -C "$source_dir" init --quiet
git -C "$source_dir" fetch --quiet --depth 1 https://github.com/ddd-4-java/ddd4j-boot.git "$revision"
git -C "$source_dir" checkout --quiet --detach FETCH_HEAD
test "$(git -C "$source_dir" rev-parse HEAD)" = "$revision"

# The registry provides the two auth JARs but omits their parent POM.
# Build the pinned adapters and their parents in dependency order. A recursive
# reactor also validates unrelated upstream sample POMs before selecting -pl.
(
  cd "$source_dir"
  for module in . ddd4j-boot-dependencies ddd4j-boot-core ddd4j-boot-web \
    ddd4j-boot-web/ddd4j-boot-web-webmvc ddd4j-boot-web/ddd4j-boot-web-webflux \
    ddd4j-boot-auth; do
    bash "$root/mvnw" -B --no-transfer-progress -N -f "$module/pom.xml" -DskipTests install
  done
)
