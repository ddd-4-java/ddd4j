#!/usr/bin/env bash
# Install the pinned Spring Boot 4 Web adapters without relying on a mutable snapshot.
set -euo pipefail

revision=f5ae36355e83259eded49ba018a8e3c8d9ecacab
root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
source_dir=$(mktemp -d "${TMPDIR:-/tmp}/ddd4j-boot4-web.XXXXXXXX")
trap 'rm -rf -- "$source_dir"' EXIT

# The adapters depend on this reactor's DDD4J Web contracts and BOM.
(cd "$root" && bash mvnw -B --no-transfer-progress \
  -pl :ddd4j-bom,:ddd4j-web-webmvc,:ddd4j-web-webflux -am -DskipTests install)

git -C "$source_dir" init --quiet
git -C "$source_dir" fetch --quiet --depth 1 https://github.com/ddd-4-java/ddd4j-boot.git "$revision"
git -C "$source_dir" checkout --quiet --detach FETCH_HEAD
test "$(git -C "$source_dir" rev-parse HEAD)" = "$revision"

(cd "$source_dir" && bash mvnw -B --no-transfer-progress \
  -pl :ddd4j-boot-web-webmvc,:ddd4j-boot-web-webflux -am -DskipTests install)
