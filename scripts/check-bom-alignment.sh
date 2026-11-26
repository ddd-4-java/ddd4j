#!/bin/bash
###############################################################################
# ddd4j-bom 版本对齐校验脚本
#
# 校验所有子模块的 pom.xml 使用 ddd4j-dependencies 统一管理的 ddd4j-* 版本号，
# 禁止子模块单独覆盖版本号（防止版本漂移）。
#
# 用法：
#   ./scripts/check-bom-alignment.sh
#
# CI 集成：
#   在 GitHub Actions / GitLab CI 中运行此脚本，失败时阻止合并
#
# 退出码：
#   0 - 全部对齐
#   1 - 存在版本漂移
###############################################################################

set -e

# 颜色
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# 项目根目录
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_ROOT"

# BOM 是发布给消费方的版本清单；ddd4j-dependencies 仅管理第三方依赖。
BOM_POM="ddd4j-bom/pom.xml"
if [ ! -f "$BOM_POM" ]; then
  echo -e "${RED}❌ 找不到 $BOM_POM${NC}"
  exit 1
fi

echo "🔍 ddd4j-bom 版本对齐校验"
echo "================================"
echo ""

# 读取所有 ddd4j-* 工件的统一版本（应使用 \${revision} 占位符）
echo -e "${YELLOW}① 校验所有子模块使用 \${revision} 版本占位符${NC}"
fail_count=0
total_modules=0

for pom in $(find . -name "pom.xml" -not -path "*/target/*" -not -path "*/node_modules/*" -not -path "*/.flattened-pom.xml"); do
  total_modules=$((total_modules + 1))
  # 找所有 ddd4j-* 工件的版本号
  bad_versions=$(grep -E "<artifactId>ddd4j-" "$pom" 2>/dev/null |
    grep -A1 "</artifactId>" | grep "<version>" |
    grep -vE "\\\${revision}|\\\${project.version}" |
    wc -l)
  if [ "$bad_versions" -gt 0 ]; then
    echo -e "${RED}  ❌ $pom 包含 $bad_versions 个硬编码 ddd4j-* 版本${NC}"
    fail_count=$((fail_count + 1))
  fi
done

# 发布 BOM 必须覆盖参与构建的公开 Jar；示例及内部测试工具允许不登记。
echo ""
echo -e "${YELLOW}② 校验公开模块的 BOM 坐标完整性${NC}"
if ! python3 - "$BOM_POM" <<'PY'
from collections import Counter
from functools import cache
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

root = Path.cwd()
internal_testkits = {"ddd4j-runtime-testkit", "ddd4j-web-testkit"}
pending = [root / "pom.xml"]
visited = set()
reactor = set()
public_jars = set()
@cache
def read_project(pom):
    try:
        project = ET.parse(pom).getroot()
    except (OSError, ET.ParseError) as error:
        sys.exit(f"{pom.relative_to(root)}: 无法读取 reactor POM ({error})")
    if project.tag.rsplit("}", 1)[-1] != "project":
        sys.exit(f"{pom.relative_to(root)}: reactor POM 缺少 project 根节点")
    return project


def group_id(project):
    return project.findtext("{*}groupId", project.findtext("./{*}parent/{*}groupId"))


@cache
def skips_deploy(pom):
    project = read_project(pom)
    flag = project.findtext("./{*}properties/{*}maven.deploy.skip")
    if flag is not None:
        return flag.strip().lower() == "true"
    parent = project.find("{*}parent")
    relative = parent.findtext("{*}relativePath", "../pom.xml").strip() if parent is not None else ""
    if not relative:
        return False
    parent_pom = (pom.parent / relative).resolve()
    if parent_pom.is_dir():
        parent_pom /= "pom.xml"
    if parent_pom == pom or not parent_pom.is_relative_to(root) or not parent_pom.is_file():
        return False
    parent_project = read_project(parent_pom)
    return (group_id(parent_project) == parent.findtext("{*}groupId")
            and parent_project.findtext("{*}artifactId") == parent.findtext("{*}artifactId")
            and skips_deploy(parent_pom))


while pending:
    pom = pending.pop()
    if pom in visited:
        continue
    visited.add(pom)
    project = read_project(pom)
    deployment_skipped = skips_deploy(pom)
    artifact = project.findtext("{*}artifactId")
    group = group_id(project)
    if group == "io.ddd4j" and artifact:
        reactor.add(artifact)
        if (project.findtext("{*}packaging", "jar") == "jar"
                and not deployment_skipped and artifact not in internal_testkits):
            public_jars.add(artifact)
    children = (project.findall("./{*}modules/{*}module")
                + project.findall("./{*}subprojects/{*}subproject"))
    for child in children:
        candidate = (pom.parent / child.text.strip()).resolve()
        module_pom = candidate if candidate.is_file() else (candidate / "pom.xml").resolve()
        if not module_pom.is_relative_to(root):
            sys.exit(f"{pom.relative_to(root)}: reactor 模块超出仓库范围 ({module_pom})")
        pending.append(module_pom)

bom = ET.parse(sys.argv[1]).getroot()
entries = bom.findall("./{*}dependencyManagement/{*}dependencies/{*}dependency")
managed = set()
declared = Counter()
for entry in entries:
    if entry.findtext("{*}groupId") != "io.ddd4j":
        continue
    artifact = entry.findtext("{*}artifactId")
    packaging = entry.findtext("{*}type", "jar")
    classifier = entry.findtext("{*}classifier", "")
    declared[artifact, packaging, classifier] += 1
    if (packaging == "jar" and not classifier and entry.findtext("{*}scope", "compile") == "compile"
            and entry.findtext("{*}version") in ("${revision}", "${project.version}")):
        managed.add(artifact)

missing = sorted(public_jars - managed)
stale = sorted({artifact for artifact, _, _ in declared} - reactor)
duplicates = sorted(key for key, count in declared.items() if count > 1)
for artifact in missing:
    print(f"  ❌ BOM 未管理公开 Jar io.ddd4j:{artifact}")
for artifact in stale:
    print(f"  ❌ BOM 指向不存在的 reactor 模块 io.ddd4j:{artifact}")
for artifact, packaging, classifier in duplicates:
    print(f"  ❌ BOM 重复坐标 io.ddd4j:{artifact}:{packaging}:{classifier}")
if missing or stale or duplicates:
    sys.exit(1)
print(f"  ✅ BOM 覆盖 {len(public_jars)} 个公开 Jar 坐标")
PY
then
  fail_count=$((fail_count + 1))
fi

echo ""
echo "================================"
echo -e "扫描模块总数: ${total_modules}"
echo -e "阻塞问题数: ${RED}${fail_count}${NC}"

if [ "$fail_count" -eq 0 ]; then
  echo -e "${GREEN}✅ 阻塞检查通过${NC}"
  exit 0
else
  echo -e "${RED}❌ 发现 ${fail_count} 项阻塞问题${NC}"
  echo ""
  echo "修复建议："
  echo "  1. 子模块 pom.xml 中所有 ddd4j-* 依赖应省略 <version> 标签"
  echo "  2. 在 ddd4j-bom/pom.xml 管理公开模块坐标，第三方版本由 ddd4j-dependencies/pom.xml 管理"
  exit 1
fi
