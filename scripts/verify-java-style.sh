#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHECKSTYLE_VERSION="${CHECKSTYLE_VERSION:-10.21.4}"
CHECKSTYLE_JAR="${HOME}/.m2/repository/com/puppycrawl/tools/checkstyle/${CHECKSTYLE_VERSION}/checkstyle-${CHECKSTYLE_VERSION}-all.jar"
CHECKSTYLE_CONFIG="${ROOT_DIR}/tools/checkstyle/need-braces-checkstyle.xml"

cd "${ROOT_DIR}"

mapfile -t JAVA_FILES < <(find . \
  \( -path '*/src/main/java/*' -o -path '*/src/test/java/*' \) \
  -type f -name '*.java' \
  ! -path '*/target/*' \
  ! -path '*/.idea/*' \
  | sort)

if [[ "${#JAVA_FILES[@]}" -eq 0 ]]; then
  echo "No Java files found under src/main/java or src/test/java."
  exit 0
fi

if [[ ! -f "${CHECKSTYLE_JAR}" ]]; then
  mvn -q -N org.apache.maven.plugins:maven-dependency-plugin:3.8.1:get \
    -Dartifact="com.puppycrawl.tools:checkstyle:${CHECKSTYLE_VERSION}:jar:all" >/dev/null
fi

TMP_MATCHES="$(mktemp)"
cleanup() {
  rm -f "${TMP_MATCHES}"
}
trap cleanup EXIT

run_guard() {
  local description="$1"
  local pattern="$2"
  shift 2

  if rg -n --pcre2 "${pattern}" "${JAVA_FILES[@]}" > "${TMP_MATCHES}"; then
    echo "[FAIL] ${description}"
    cat "${TMP_MATCHES}"
    return 1
  fi

  echo "[PASS] ${description}"
}

run_guard "forbid object null comparisons" '(?<![\w.])(?:this\.)?[\w$<>\[\].()]+?\s*(?:==|!=)\s*null'
run_guard "forbid String.isBlank()" '\.isBlank\(\)'
run_guard "forbid trim().isEmpty()" 'trim\(\)\.isEmpty\(\)'
run_guard "forbid manual string null-or-empty checks" '(==\s*null\s*\|\|\s*[^;]*\.isEmpty\(\))|(!=\s*null\s*&&\s*[^;]*!\s*[^;]*\.isEmpty\(\))'
run_guard "forbid direct logger factory usage in application code" 'LoggerFactory\.getLogger\(' \
  --glob '!**/target/**' \
  --glob '!**/.idea/**' \
  --glob '!**/src/test/**' \
  --glob '!**/src/main/java/**/RobotLogbackAppendService.java'

java -jar "${CHECKSTYLE_JAR}" -c "${CHECKSTYLE_CONFIG}" "${JAVA_FILES[@]}"

echo "Java style verification passed."
