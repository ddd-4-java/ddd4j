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

# 版本属性只允许一个维护位置；相同值的父子重复定义也会在后续升级时漂移。
duplicate_properties=$(python3 - "pom.xml" "ddd4j-dependencies/pom.xml" <<'PY'
import sys
import xml.etree.ElementTree as ET

def properties(path):
    root = ET.parse(path).getroot()
    namespace = root.tag.partition("}")[0] + "}" if "}" in root.tag else ""
    properties = root.find(namespace + "properties")
    if properties is None:
        return {}
    return {element.tag.rsplit("}", 1)[-1]: (element.text or "").strip()
            for element in properties}

parent = properties(sys.argv[1])
dependencies = properties(sys.argv[2])
for name in sorted(parent.keys() & dependencies.keys()):
    if name.endswith(".version"):
        print(f"{name}: pom.xml={parent[name]}, ddd4j-dependencies/pom.xml={dependencies[name]}")
if parent.get("revision", "").startswith("2.0.x"):
    if parent.get("java.version") != "17":
        print("java.version: 2.0.x requires JDK 17")
    if not dependencies.get("jakarta-servlet.version", "").startswith("6."):
        print("jakarta-servlet.version: 2.0.x requires Jakarta Servlet 6.x")
    compatible = {
        "spring-framework.version": "6.", "spring-security.version": "6.",
        "spring-amqp.version": "3.", "spring-authorization-server.version": "1.",
        "spring-batch.version": "5.", "spring-boot3-it.version": "3.",
        "spring-data-bom.version": "2025.0.", "spring-graphql.version": "1.",
        "spring-hateoas.version": "2.", "spring-integration.version": "6.",
        "spring-kafka.version": "3.", "spring-ldap.version": "3.",
        "spring-pulsar.version": "1.", "spring-restdocs.version": "3.",
        "spring-session.version": "3.", "spring-ws.version": "4.",
    }
    for name, prefix in compatible.items():
        value = dependencies.get(name)
        if (value is not None or name == "spring-framework.version") and not (value or "").startswith(prefix):
            print(f"{name}: 2.0.x Spring 6 requires {prefix}x, found {value}")
PY
)
if [ -n "$duplicate_properties" ]; then
  echo -e "${RED}  ❌ 版本属性归属或平台边界错误：${NC}"
  echo "$duplicate_properties"
  fail_count=$((fail_count + 1))
fi

# 只检查根 POM 声明的 Maven 3 模块和 Maven 4 子项目。
reactor_report=$(python3 - <<'PY'
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

root = Path.cwd()
pending = [root / "pom.xml"]
visited = set()
failures = []
while pending:
    pom = pending.pop()
    if pom in visited:
        continue
    try:
        project = ET.parse(pom).getroot()
    except (OSError, ET.ParseError) as error:
        sys.exit(f"{pom.relative_to(root)}: 无法读取 reactor POM ({error})")
    visited.add(pom)
    namespace = project.tag.partition("}")[0] + "}" if "}" in project.tag else ""
    if project.tag != namespace + "project":
        sys.exit(f"{pom.relative_to(root)}: reactor POM 缺少 project 根节点")

    # 包括 dependencyManagement 和 profile 内的依赖，排除父项目版本。
    bad_versions = 0
    for dependency in project.iter(namespace + "dependency"):
        group = dependency.findtext(namespace + "groupId")
        artifact = dependency.findtext(namespace + "artifactId", "")
        version = dependency.findtext(namespace + "version")
        if (group == "io.ddd4j" and artifact.startswith("ddd4j-")
                and version is not None and version not in ("${revision}", "${project.version}")):
            bad_versions += 1
    if bad_versions:
        failures.append((pom.relative_to(root), bad_versions))

    for container, entry in (("modules", "module"), ("subprojects", "subproject")):
        for module in project.findall(f"{namespace}{container}/{namespace}{entry}"):
            path = (pom.parent / (module.text or "").strip()).resolve()
            module_pom = path if path.is_file() else (path / "pom.xml").resolve()
            if not module_pom.is_relative_to(root):
                sys.exit(f"{pom.relative_to(root)}: reactor 模块超出仓库范围 ({module_pom})")
            pending.append(module_pom)

print(len(visited))
for pom, count in sorted(failures):
    print(f"./{pom} 包含 {count} 个硬编码 ddd4j-* 版本")
PY
)
{
  IFS= read -r total_modules
  while IFS= read -r issue; do
    echo -e "${RED}  ❌ $issue${NC}"
    fail_count=$((fail_count + 1))
  done
} <<< "$reactor_report"

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
