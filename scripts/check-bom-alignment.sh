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
    bad_versions=$(grep -E "<artifactId>ddd4j-" "$pom" 2>/dev/null | \
        grep -A1 "</artifactId>" | grep "<version>" | \
        grep -vE "\\\${revision}|\\\${project.version}" | \
        wc -l)
    if [ "$bad_versions" -gt 0 ]; then
        echo -e "${RED}  ❌ $pom 包含 $bad_versions 个硬编码 ddd4j-* 版本${NC}"
        fail_count=$((fail_count + 1))
    fi
done

echo ""
echo -e "${YELLOW}② 校验关键 ddd4j-* 工件在 BOM 中存在（警告级，非阻塞）${NC}"
# 聚合 POM 不会作为依赖消费，避免将 ddd4j-data/mq/web/runtime 等聚合器误报为
# 应进入 BOM 的工件。这里保留每个基础能力的公开入口作为回归锚点。
required_artifacts=(
    "ddd4j-core"
    "ddd4j-annotation"
    "ddd4j-kit"
    "ddd4j-cache"
    "ddd4j-ddd-rules"
    "ddd4j-data-mybatis"
    "ddd4j-mq-core"
    "ddd4j-web-core"
    "ddd4j-runtime-spring"
)
warn_count=0
for art in "${required_artifacts[@]}"; do
    if rg -q -F "<artifactId>$art</artifactId>" "$BOM_POM"; then
        echo -e "${GREEN}  ✅ $art 在 BOM 中${NC}"
    else
        echo -e "${YELLOW}  ⚠️  $art 不在 BOM 中（建议补充到 dependencyManagement）${NC}"
        warn_count=$((warn_count + 1))
    fi
done

echo ""
echo "================================"
echo -e "扫描模块总数: ${total_modules}"
echo -e "阻塞问题数（硬编码版本）: ${RED}${fail_count}${NC}"
echo -e "警告数（BOM 缺失）: ${YELLOW}${warn_count}${NC}"

if [ "$fail_count" -eq 0 ]; then
    echo -e "${GREEN}✅ 阻塞检查通过（硬编码版本问题已修复）${NC}"
    if [ "$warn_count" -gt 0 ]; then
        echo -e "${YELLOW}⚠️  建议补充 BOM（见警告）${NC}"
    fi
    exit 0
else
    echo -e "${RED}❌ 发现 ${fail_count} 个硬编码版本问题${NC}"
    echo ""
    echo "修复建议："
    echo "  1. 子模块 pom.xml 中所有 ddd4j-* 依赖应省略 <version> 标签"
    echo "  2. 版本号统一在 ddd4j-dependencies/pom.xml 的 <dependencyManagement> 中管理"
    exit 1
fi
