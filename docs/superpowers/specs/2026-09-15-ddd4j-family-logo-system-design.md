# ddd4j 六组件统一 Logo 系统设计

## 目标

为 `ddd4j`、`ddd4j-boot`、`ddd4j-javalin`、`ddd4j-quarkus`、`ddd4j-web3`、`ddd4j-cloud`
建立同一品牌家族下的六个可辨识图标。封面图和内容图保持现有构图质量，本轮只重做 Logo，并定义后续
在封面与内容图中替换 Logo、主题光和强调部件的规则。

参考 `/Users/wandl/workspaces/workspace-github/_org-readmes/LOGO_SYSTEM.md` 的 Bright Aurora 方向：明亮背景、
等距立方体、清晰技术符号、头像安全区。不得复制已有单个组织 Logo；本系列必须形成自己的组件层级。

## 品牌架构

所有图标共享同一个不可变母体：

1. 三枚等距立方体组成稳定三角，表示 Domain、Application、Infrastructure。
2. 中央负形与两道内框形成 COLA 分层切面，表示清晰边界与依赖方向。
3. 深墨蓝轮廓、统一 30° 等距透视、统一圆角、统一线宽、统一光源。
4. 右下技术面承载组件专属符号；技术符号不得脱离母体独立漂浮。
5. 独立图标不放长字标；横向锁定版统一为深墨蓝 `ddd4j` 加主题色后缀。

## 六个组件的专属语义

| 组件 | 共享语义 | 专属技术符号 | 视觉含义 |
| --- | --- | --- | --- |
| `ddd4j` | DDD + COLA | Java 蒸汽曲线 + 基石托座 | Java DDD 基础构件与架构基石 |
| `ddd4j-boot` | DDD + COLA | Spring 叶片 + 启动弧/电源点 | Spring Boot 自动装配与启动 |
| `ddd4j-javalin` | DDD + COLA | Javalin 帆形 + 轻量请求流线 | 轻量 HTTP 服务与快速路由 |
| `ddd4j-quarkus` | DDD + COLA | Q 形原生切面 + 加速星芒 | Quarkus Native 与快速启动 |
| `ddd4j-web3` | DDD + COLA | 六边形代币 + 链接节点 + 锁孔 | 加密货币、链上账本与可信边界 |
| `ddd4j-cloud` | DDD + COLA | Spring 叶片 + 云弧 + 三服务节点 | Spring Cloud 分布式服务体系 |

技术符号采用原创几何抽象，不直接复制第三方商标；应能表达生态含义，同时保持 ddd4j 母体主导。

## 色彩体系

### 家族共享色

| Token | 色值 | 用途 |
| --- | --- | --- |
| `family-ink` | `#10233F` | 字标、深色轮廓、暗面 |
| `family-core` | `#2457D6` | DDD 核心立方体、母品牌主色 |
| `family-cyan` | `#2AB7CA` | COLA 分层线、连接与高光 |
| `family-surface` | `#F7FAFF` | 明亮背景 |
| `family-line` | `#D8E5F5` | 安全边框与弱分隔 |

### 组件主题色

| 组件 | 名称 | Default | Dark | Soft |
| --- | --- | --- | --- | --- |
| `ddd4j` | 基石蓝 | `#2457D6` | `#183E9E` | `#EEF4FF` |
| `ddd4j-boot` | 装配绿 | `#5B8F3A` | `#3E6726` | `#F1F8EC` |
| `ddd4j-javalin` | 轻量橙 | `#E66A2C` | `#AD451A` | `#FFF3EC` |
| `ddd4j-quarkus` | 原生莓紫 | `#A54078` | `#782B57` | `#FBEEF5` |
| `ddd4j-web3` | 账本紫 | `#6A4DD8` | `#4932A2` | `#F2EFFF` |
| `ddd4j-cloud` | 云端青 | `#1687A7` | `#0F6078` | `#EAF8FB` |

产品主题色只表达组件身份。成功、警告、错误、未知状态继续使用独立状态色，不能由产品色代替。

## 图形与导出规范

- Master：SVG，`viewBox="0 0 512 512"`，矢量优先。
- PNG：透明背景 1024×1024，供仓库头像、README 和封面合成使用。
- 横向锁定版：SVG 与透明 PNG，图标在左，字标在右。
- 图标安全区：四边至少 48px；圆形裁切后技术符号和母体不得被切断。
- 尺寸门禁：128px、48px、24px 均须可辨；24px 允许隐藏内部次级分层线，但不能更换轮廓。
- 明暗门禁：浅色、深色、单色、灰度均须保持轮廓辨识。
- 不使用照片级 3D、复杂阴影、不同透视、独立徽章底座、随机字体或每个项目不同的图标容器。

## 主题色比较稿

创建 `docs/branding/主题色比较稿.html`，内容包括：

1. 家族关系与母体说明。
2. 家族共享色和六组件主题色完整色表。
3. 六个图标的统一尺寸并排预览。
4. 浅色、深色、灰度背景比较。
5. 128px、48px、24px 可识别性检查。
6. 组件色与成功/警告/错误状态色并排，防止语义混用。
7. 封面图与内容图的颜色映射示例，仅展示关系，不重做现有图片。

## 六仓库交付路径

新资产不得覆盖现有 Logo，先以 `-v2` 文件名交付：

| 仓库 | 新 Logo 目录 |
| --- | --- |
| `ddd4j` | `assets/branding/` |
| `ddd4j-boot` | `docs/assets/branding/` |
| `ddd4j-javalin` | `docs/assets/brand/` |
| `ddd4j-quarkus` | `docs/assets/branding/` |
| `ddd4j-web3` | `assets/branding/` |
| `ddd4j-cloud` | `docs/assets/branding/` |

每个目录交付 `<project>-icon-v2.svg`、`<project>-icon-v2.png`、`<project>-logo-v2.svg`、
`<project>-logo-v2.png`。用户确认整组后，才允许替换 README 或封面图引用。

## 验收标准

1. 任意两个图标并排时能看出同一母体、同一透视、同一线宽和同一光源。
2. 隐去字标后仍能通过技术符号和主题色区分六个组件。
3. `ddd4j-web3` 同时表达 DDD/COLA 与加密货币/链上可信；`ddd4j-cloud` 同时表达 DDD/COLA 与
   Spring Cloud；Boot、Javalin、Quarkus 分别具有明确生态符号。
4. `ddd4j` 的基石托座和 Java 蒸汽表达其母体地位，而不是普通框架适配器。
5. HTML 色表、SVG 色值、PNG 导出结果一致。
6. 原 Logo、封面图和内容图不被覆盖或删除。
