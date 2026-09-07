#!/usr/bin/env bash

set -euo pipefail

if [[ "${JAVA_HOME:-}" == "" ]]; then
  echo "ERROR: JAVA_HOME must point to the branch JDK" >&2
  exit 2
fi

repo_root=$(cd "$(dirname "$0")/.." && pwd -P)
fixture_root=$(mktemp -d /tmp/ddd4j-parent-consumer.XXXXXX)
local_repo="$fixture_root/repository"
consumer_dir="$fixture_root/consumer"
maven_cmd=${MAVEN_CMD:-$repo_root/mvnw}
mkdir -p "$consumer_dir"

cleanup() {
  rm -rf "$fixture_root"
}
trap cleanup EXIT

"$maven_cmd" \
  -q \
  -Dmaven.repo.local="$local_repo" \
  -DskipTests \
  -f "$repo_root/pom.xml" \
  -pl ddd4j-parent \
  -am \
  install

cat > "$consumer_dir/pom.xml" <<'POM'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>io.ddd4j</groupId>
        <artifactId>ddd4j-parent</artifactId>
        <version>1.0.x.20260630-SNAPSHOT</version>
        <relativePath/>
    </parent>
    <groupId>io.ddd4j.consumer</groupId>
    <artifactId>boot-line-fixture</artifactId>
    <version>${revision}</version>
    <properties>
        <revision>2.7.x.20260630-SNAPSHOT</revision>
        <ddd4j.version>1.0.x.20260630-SNAPSHOT</ddd4j.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>io.ddd4j</groupId>
            <artifactId>ddd4j-core</artifactId>
        </dependency>
    </dependencies>
</project>
POM

"$maven_cmd" \
  -q \
  -Dmaven.repo.local="$local_repo" \
  -f "$consumer_dir/pom.xml" \
  help:effective-pom \
  -Doutput="$fixture_root/effective-pom.xml"

managed_version=$(awk '
  /<artifactId>ddd4j-core<\/artifactId>/ { found = 1; next }
  found && /<version>/ {
    value = $0
    sub(/^.*<version>/, "", value)
    sub(/<\/version>.*$/, "", value)
    print value
    exit
  }
' "$fixture_root/effective-pom.xml")

if [[ "$managed_version" == "" ]]; then
  echo "ERROR: effective POM does not manage ddd4j-core" >&2
  exit 1
fi

if [[ "$managed_version" != "1.0.x.20260630-SNAPSHOT" ]]; then
  echo "ERROR: expected ddd4j-core 1.0.x.20260630-SNAPSHOT, got $managed_version" >&2
  exit 1
fi

echo "PASS: external consumer revision does not override the ddd4j BOM version"
