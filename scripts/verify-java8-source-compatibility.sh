#!/usr/bin/env bash
set -euo pipefail

if rg -n --glob '*.java' '@Deprecated\([^)]*(since|forRemoval)' ddd4j-annotation ddd4j-core ddd4j-data ddd4j-ddd ddd4j-extensions ddd4j-kit ddd4j-monitor ddd4j-mq ddd4j-web; then
  echo "Java 8 source compatibility violation: Deprecated.since/forRemoval requires Java 9 or newer." >&2
  exit 1
fi

if rg -n --glob 'package-info.java' '@deprecated' ddd4j-annotation ddd4j-core ddd4j-data ddd4j-ddd ddd4j-extensions ddd4j-kit ddd4j-monitor ddd4j-mq ddd4j-web; then
  echo "Java 8 source compatibility violation: package-info @deprecated is rejected by javac 8." >&2
  exit 1
fi

if rg -n --glob '*.java' '^[[:space:]]*(public|protected|private)?[[:space:]]*record[[:space:]]+[A-Za-z_]|^[[:space:]]*(public|protected|private)?[[:space:]]*(sealed|non-sealed)[[:space:]]+(class|interface)|case[[:space:]].+->[[:space:]]|\byield[[:space:]]+' ddd4j-annotation ddd4j-core ddd4j-data ddd4j-ddd ddd4j-extensions ddd4j-kit ddd4j-monitor ddd4j-mq ddd4j-web; then
  echo "Java 8 source compatibility violation: record, sealed, switch expression, or yield is present." >&2
  exit 1
fi

if rg -n --glob '*.java' '\b(Map|List|Set)\.of\(|\.toList\(\)|\.isBlank\(\)|\.canAccess\(' ddd4j-annotation ddd4j-core ddd4j-data ddd4j-ddd ddd4j-extensions ddd4j-kit ddd4j-monitor ddd4j-mq ddd4j-web; then
  echo "Java 8 source compatibility violation: Java 9+ collection, String, reflection, or Stream APIs are present." >&2
  exit 1
fi

echo "PASS: Java 8 source compatibility"
