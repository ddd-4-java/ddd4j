# 框架分支统一约定

- 所有版本、修复、集成及备份分支禁止声明 Java `record` 类型，包括生产代码、测试、样例及受 Git 管理的 Java 文档片段。
- 值对象使用普通类；不可变模型使用 final 类、final 字段及显式构造器。保留既有组件名访问器、构造验证、值相等性和 JSON 契约，不为跨分支一致性升级 JDK 或依赖。
- 提交前执行 `python3 scripts/check_no_records.py`；检查忽略字符串与注释，只拒绝实际类型声明。检查器自测执行 `python3 -m unittest discover -s scripts -p test_check_no_records.py`。
- 涉及类型迁移时必须执行受影响模块构建与行为回归；源码扫描通过不代替编译、序列化或业务验证。

# 绘图与可视化规则

本节整合 `draw_architecture_rules.mdc`、`draw_mermaid_rules.mdc` 和 `draw_mindmap_rules.mdc`。仅在架构图、流程图、思维导图及相关可视化任务中应用，不要求普通代码任务生成图形或 HTML。

## 通用设计与输出

- 根据需求选择图表类型，准确表达组件、层次、调用关系、数据流和失败路径。
- 使用简洁的 iOS 现代风格，可搭配 Material Design 3；统一自定义配色，覆盖绘图库默认主题。
- 默认纯白背景、中文标注、圆角矩形；可使用蓝、绿、橙等颜色区分职责，保证文字对比度和整体风格统一。
- 布局紧凑、层次清晰，兼顾阅读和操作体验；HTML 图形容器默认 `padding: 10px`，按内容调整。
- 架构图默认左侧显示分层标识、右侧显示具体内容；分组采用大框套小框的递进结构，节点内容居中对齐。
- 图表、节点和文件采用功能导向的命名，避免装饰影响信息表达。
- 交付独立可视化文件时，使用 HTML + SVG 格式，保存到 `html/{分类}/`；交互思维导图可采用 HTML 中嵌入画布的方式，并按需要提供 SVG 等导出产物。
- 在答复或 Markdown 文档中说明架构时，可直接使用 Mermaid 代码块；用户指定输出格式或保存路径时优先遵循。

## Markdown 与 Mermaid

- 使用 Markdown 组织说明，使用 Mermaid 绘制专业图形；HTML 页面需要渲染 Markdown 时使用 Marked。
- 按图表目的选择语法；下表保留原规则中的类型索引，实际使用前核对所采用 Mermaid 版本是否支持，尤其是实验性语法。

| 图表类型 | Mermaid 关键词 |
|---|---|
| 流程图 | `graph` / `flowchart` |
| 时序图 | `sequenceDiagram` |
| 类图 | `classDiagram` |
| 状态图 | `stateDiagram` |
| 实体关系图 | `erDiagram` |
| 用户旅程图 | `journey` |
| 甘特图 | `gantt` |
| 饼图 | `pie` |
| 象限图 | `quadrantChart` |
| 需求图 | `requirementDiagram` |
| Gitgraph 图 | `gitGraph` |
| C4 图 | `C4Context` |
| 思维导图 | `mindmap` |
| 时间线图 | `timeline` |
| ZenUML | `zenuml` |
| 桑基图 | `sankey` |
| XY 图 | `xychart` |
| 方块图 | `block` |
| 数据包图 | `packet` |
| 看板 | `kanban` |
| 架构图 | `architecture-beta` |
| 雷达图 | `radar-beta` |
| 树状图 | `treemap-beta` |

- HTML 集成：通过 script 标签加载 Marked/Mermaid，将 Markdown 渲染到内容容器，将 Mermaid 源码放入 `class="mermaid"` 的容器，并按对应版本完成初始化。
- 交付页面应使用明确的库版本；原规则的无版本 CDN 地址仅作为集成示例，不作为固定版本依据。
- 官方资料：[Marked 文档](https://marked.js.org/)、[Marked 源码](https://github.com/markedjs/marked)、[Mermaid 文档](https://mermaid.js.org/intro/)、[Mermaid 源码](https://github.com/mermaid-js/mermaid)。各图表语法从 Mermaid 官方文档进入核对。

## SimpleMindMap

- 需要交互思维导图时，使用 Markdown 与 SimpleMindMap，按需加载插件，避免引入无关功能。
- 根据内容选择逻辑结构图、思维导图、组织结构图、目录组织图、时间轴或鱼骨图等布局；使用自定义主题和配色。
- 按需支持普通文本或富文本、图片、图标、超链接、备注、标签、概要和数学公式；可自定义节点形状及节点内容。
- 按需启用节点拖拽、多选、画布移动与缩放、快捷键、撤销重做、关联线、搜索替换、小地图、水印、滚动条、手绘风格、彩虹线条、标记和外框。
- 按任务需要提供协同编辑与演示模式，不默认要求接入这些功能。
- 按所采用版本和插件支持情况，提供 JSON、PNG、SVG、PDF、Markdown、XMind、TXT 导出，以及 JSON、XMind、Markdown 导入。
- HTML 集成：加载匹配版本的样式与脚本，创建 `id="mindMapContainer"` 的容器并初始化；该容器无需添加 Mermaid 类名。需要重置节点样式时，可限定在 `#mindMapContainer` 内设置 `margin: 0; padding: 0;`。
- 原规则中的 CDN 示例版本为 `0.14.0`；实际交付时核对所用版本及 API，不将该示例当作最新版本承诺。
- 官方资料：[SimpleMindMap 文档](https://wanglin2.github.io/mind-map-docs/start/start.html)、[SimpleMindMap 源码](https://github.com/wanglin2/mind-map)。

# Maven 依赖维护规则

本节整合 `mavenrules.mdc`，仅在用户要求排查 Maven 问题或升级依赖时应用；读取项目、分析架构或编辑文档不触发依赖升级。

1. 先检查当前分支的 JDK、Maven Wrapper、POM 模型、依赖集中管理位置和已有 Versions 插件配置；优先使用项目 `./mvnw`。
2. 需要查询升级候选时，使用 Versions 插件的 `versions:display-dependency-updates`。若尚无插件配置，先评估显式调用插件目标；确需持久化配置时，在已授权的依赖维护范围内增量添加到对应 POM 的 `<build>` 配置。
3. 对照现有 POM 和更新列表选择候选版本，拒绝新增 preview、Alpha、RC、MD、BETA 等预发布升级候选；优先选择命名规范的稳定版本，如 `3.21`、`4.2.7.Final`、`22.0.0.RELEASE`。该筛选规则不要求替换项目已经确认使用的预发布工具或依赖。
4. 核对候选版本与当前 JDK、框架、BOM、命名空间和公共 API 的兼容性，不能仅根据版本号较新就升级。
5. 优先更新集中管理位置中已有的版本号或属性；任务未要求时，不向具体模块新增版本属性，不分散已有版本事实源。
6. 修改后执行 `./mvnw compile` 或受影响模块的等价构建，并运行受影响行为回归及项目要求的依赖、BOM 和模型检查；编译通过不代替兼容性或业务验收。
7. 报告实际更新的坐标、版本和验证结果；环境阻塞或未执行的检查明确说明，不宣称已通过。
