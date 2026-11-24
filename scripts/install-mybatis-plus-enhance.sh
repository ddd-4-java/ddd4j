#!/usr/bin/env bash
set -euo pipefail

line="${1:?Usage: install-mybatis-plus-enhance.sh <line> <project-root>}"
project_root="$(cd "${2:?Project root is required}" && pwd -P)"
case "$line" in
  1.0.x)
    annotation_sha=d7aeffe0ca92692113a587756e79937ed29157b8
    enhance_sha=9becfc2b0f7b2fae7173ae31201f466cd8ec0758
    ;;
  3.0.x)
    annotation_sha=c90c25cd3e8c1f37d455058e50e8a6572b7981ff
    enhance_sha=f5aeb04a69d7045f7a3c086045238737c880d20f
    ;;
  *)
    echo "Unsupported MyBatis-Plus Enhance line: $line" >&2
    exit 1
    ;;
esac

source_dir="$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/ddd4j-mybatis-enhance.XXXXXX")"
trap 'rm -rf "$source_dir"' EXIT

checkout_dependency() {
  local repository="$1" revision="$2" destination="$3"
  git init --quiet "$destination"
  git -C "$destination" fetch --quiet --depth 1 "https://github.com/easy-4-java/$repository.git" "$revision"
  git -C "$destination" checkout --quiet --detach FETCH_HEAD
  test "$(git -C "$destination" rev-parse HEAD)" = "$revision"
  echo "Building $repository at $revision for $line"
}

checkout_dependency mybatis-enhance "$annotation_sha" "$source_dir/annotation"
checkout_dependency mybatis-plus-enhance "$enhance_sha" "$source_dir/plus"

cd "$project_root"
maven=(sh ./mvnw -B --no-transfer-progress -DskipTests -Djacoco.skip=true -Dgpg.skip=true)
"${maven[@]}" -f "$source_dir/annotation/pom.xml" -pl mybatis-enhance-annotation -am install
"${maven[@]}" -f "$source_dir/plus/pom.xml" -pl mybatis-plus-enhance-extension -am install
echo "Installed MyBatis-Plus Enhance for $line from $enhance_sha"
