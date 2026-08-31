#!/usr/bin/env bash
set -euo pipefail

if ! rg -q '<java.version>1\.8</java.version>' pom.xml; then
  echo "JDK 8 branch must declare java.version 1.8." >&2
  exit 1
fi

if ! rg -q '<junit-jupiter.version>5\.' ddd4j-dependencies/pom.xml; then
  echo "JDK 8 branch must use a JUnit 5 platform, not JUnit 6." >&2
  exit 1
fi

echo "PASS: Java 8 dependency baseline"
