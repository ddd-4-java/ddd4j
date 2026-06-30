#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHECKSTYLE_CONFIG="${ROOT_DIR}/tools/checkstyle/need-braces-checkstyle.xml"

cd "${ROOT_DIR}"

JAVA_FILES=()
while IFS= read -r file; do
  JAVA_FILES+=("${file}")
done < <(find . \
  \( -path '*/src/main/java/*' -o -path '*/src/test/java/*' \) \
  -type f -name '*.java' \
  ! -path '*/target/*' \
  ! -path '*/.idea/*' \
  | sort)

if [[ "${#JAVA_FILES[@]}" -eq 0 ]]; then
  echo "No Java files found under src/main/java or src/test/java."
  exit 0
fi

TMP_MATCHES="$(mktemp)"
TMP_DIR="$(mktemp -d)"
TMP_POM="${TMP_DIR}/pom.xml"
cleanup() {
  rm -f "${TMP_MATCHES}"
  rm -rf "${TMP_DIR}"
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

run_guard "forbid object null comparisons" '^(?!\s*(?:\*|//)).*(?<![\w.])(?:this\.)?[\w$<>\[\].()]+?\s*(?:==|!=)\s*null'
run_guard "forbid String.isBlank()" '\.isBlank\(\)'
run_guard "forbid trim().isEmpty()" 'trim\(\)\.isEmpty\(\)'
run_guard "forbid manual string null-or-empty checks" '(==\s*null\s*\|\|\s*[^;]*\.isEmpty\(\))|(!=\s*null\s*&&\s*[^;]*!\s*[^;]*\.isEmpty\(\))'
run_guard "forbid direct logger factory usage in application code" 'LoggerFactory\.getLogger\(' \
  --glob '!**/target/**' \
  --glob '!**/.idea/**' \
  --glob '!**/src/test/**' \
  --glob '!**/src/main/java/**/RobotLogbackAppendService.java'

cat > "${TMP_POM}" <<EOF
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>io.ddd4j.tools</groupId>
  <artifactId>java-style-check</artifactId>
  <version>1.0.0</version>
</project>
EOF

mvn -q -f "${TMP_POM}" org.apache.maven.plugins:maven-checkstyle-plugin:3.6.0:check \
  -Dcheckstyle.config.location="${CHECKSTYLE_CONFIG}" \
  -Dcheckstyle.includes='**/src/main/java/**/*.java,**/src/test/java/**/*.java' \
  -Dcheckstyle.excludes='**/target/**,**/.idea/**' \
  -Dcheckstyle.sourceDirectories="${ROOT_DIR}" \
  -Dcheckstyle.failOnViolation=true

echo "Java style verification passed."
