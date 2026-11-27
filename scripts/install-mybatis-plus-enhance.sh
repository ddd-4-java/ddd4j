#!/usr/bin/env bash
set -euo pipefail

line="${1:?Usage: install-mybatis-plus-enhance.sh <line> <project-root>}"
project_root="$(cd "${2:?Project root is required}" && pwd -P)"
case "$line" in
  1.0.x)
    annotation_sha=d7aeffe0ca92692113a587756e79937ed29157b8
    enhance_sha=509ab8b0133ee43cbee34f42bb95d01014bf02d5
    validation_sha=b6576da2fad6b3c091b1711dd9deb61096f9a4d1
    ;;
  2.0.x)
    annotation_sha=0fdb0d32f8fdc1840e232414f92166b53bab1d6d
    enhance_sha=d2a9520a978bcff974b76dba5b5436f1a21021d2
    validation_sha=f800df7327d27bdc39a83893ba841e24b216236a
    ;;
  3.0.x)
    annotation_sha=c90c25cd3e8c1f37d455058e50e8a6572b7981ff
    enhance_sha=c0dd5cdc0138f961adf9f465a9f7d608c8a01b00
    validation_sha=f6d38c514d2eed54aa3929d4e210686a1cbe6cf5
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
checkout_dependency validation-api-extension "$validation_sha" "$source_dir/validation"
if [[ "$line" == 2.0.x ]]; then
  # This revision uses Reactor's contextWrite, required by the Spring 6 runtime.
  checkout_dependency spring-extension 8386dec0e00ed6b23a853188db2028c3eb92bedc "$source_dir/spring-extension"
fi

cd "$project_root"
maven=(sh ./mvnw -B --no-transfer-progress -DskipTests -Djacoco.skip=true -Dgpg.skip=true)

install_dependency() {
  local log="$source_dir/wrapper-install.log"
  local attempt
  for attempt in 1 2 3; do
    if "${maven[@]}" "$@" 2>&1 | tee "$log"; then
      return 0
    fi
    # Retry only Maven Wrapper distribution downloads; Maven build failures stay fatal.
    if [[ "$attempt" == 3 ]] || ! grep -Eq '^wget: Failed to fetch https://(repo\.maven\.apache\.org/maven2/org/apache/maven/apache-maven/|archive\.apache\.org/dist/maven/)' "$log"; then
      return 1
    fi
    echo "Retrying Maven Wrapper distribution download ($attempt/3)" >&2
    sleep "$((attempt * 3))"
  done
}

install_dependency -f "$source_dir/annotation/pom.xml" -pl mybatis-enhance-annotation -am install
install_dependency -f "$source_dir/plus/pom.xml" -pl mybatis-plus-enhance-extension -am install
install_dependency -f "$source_dir/validation/pom.xml" install
echo "Installed MyBatis-Plus Enhance and Validation API Extension for $line"
if [[ "$line" == 2.0.x ]]; then
  install_dependency -f "$source_dir/spring-extension/pom.xml" install
fi
