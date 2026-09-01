#!/usr/bin/env bash
set -euo pipefail

# 扫描范围：JDK8 反应堆内的全部模块目录
DIRS=(ddd4j-annotation ddd4j-core ddd4j-data ddd4j-data-event-store-jpa ddd4j-data-event-store-jdbi ddd4j-data-event-store-r2dbc ddd4j-ddd ddd4j-extensions ddd4j-kit ddd4j-monitor ddd4j-mq ddd4j-web)

# 以下两个模块不在 JDK8 反应堆内（尚未完成 Java 8 移植，见 ddd4j-extensions/pom.xml 注释）；
# 重新纳入反应堆时请删除这两条排除。
EXCLUDES=(-g '!ddd4j-extensions/ddd4j-extension-qlexpress/**' -g '!ddd4j-extensions/ddd4j-mq-mqtt-mica/**')

if rg -n -g '*.java' "${EXCLUDES[@]}" '@Deprecated\([^)]*(since|forRemoval)' "${DIRS[@]}"; then
  echo "Java 8 source compatibility violation: Deprecated.since/forRemoval requires Java 9 or newer." >&2
  exit 1
fi

if rg -n -g 'package-info.java' "${EXCLUDES[@]}" '@deprecated' "${DIRS[@]}"; then
  echo "Java 8 source compatibility violation: package-info @deprecated is rejected by javac 8." >&2
  exit 1
fi

if rg -n -g '*.java' "${EXCLUDES[@]}" '^[[:space:]]*(public|protected|private)?[[:space:]]*record[[:space:]]+[A-Za-z_]|^[[:space:]]*(public|protected|private)?[[:space:]]*(sealed|non-sealed)[[:space:]]+(class|interface)|case[[:space:]].+->[[:space:]]|\byield[[:space:]]+' "${DIRS[@]}"; then
  echo "Java 8 source compatibility violation: record, sealed, switch expression, or yield is present." >&2
  exit 1
fi

# Stream.toList()（Java 16）需排除 java.util.stream.Collectors.toList()（Java 8 合法）。
# rg 的正则引擎不支持后顾断言，用管道过滤 Collectors 误报。
if rg -n -g '*.java' "${EXCLUDES[@]}" '\b(Map|List|Set)\.of\(|\.isBlank\(\)|\.canAccess\(' "${DIRS[@]}" \
  || rg -n -g '*.java' "${EXCLUDES[@]}" '\.toList\(\)' "${DIRS[@]}" | command grep -v 'Collectors\.toList()'; then
  echo "Java 8 source compatibility violation: Java 9+ collection, String, reflection, or Stream APIs are present." >&2
  exit 1
fi

echo "PASS: Java 8 source compatibility"
