#!/usr/bin/env bash

set -euo pipefail

if [[ "${JAVA_HOME:-}" == "" ]]; then
  echo "ERROR: JAVA_HOME must point to the branch JDK" >&2
  exit 2
fi

repo_root=$(cd "$(dirname "$0")/.." && pwd -P)
set +e
version_output=$(cd "$repo_root" && ./mvnw -version 2>&1)
wrapper_status=$?
set -e

if [[ "$wrapper_status" -ne 0 ]]; then
  echo "ERROR: Maven wrapper cannot run on JDK 8" >&2
  echo "$version_output" >&2
  exit 1
fi

if [[ "$version_output" != *"Apache Maven 3."* ]]; then
  echo "ERROR: feature/1.0.x must use a Maven 3 wrapper on JDK 8" >&2
  echo "$version_output" >&2
  exit 1
fi

if [[ "$version_output" != *"Java version: 1.8"* ]]; then
  echo "ERROR: wrapper did not run on JDK 8" >&2
  echo "$version_output" >&2
  exit 1
fi

echo "PASS: feature/1.0.x Maven wrapper runs on JDK 8"
