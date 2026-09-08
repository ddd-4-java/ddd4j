# ZXing 扩展坐标迁移证据

2026-09-08 核验以下发布 JAR：

- `io.github.hiwepy:zxing-extension:2.0.x.20260630-SNAPSHOT`：含 Java 21 字节码，不能用于 Java 17。
- `io.github.easy4j:zxing-extension:2.0.x.20260630-SNAPSHOT`：Java 17 产物。
- `io.github.hiwepy:zxing-extension:3.0.x.20260630-SNAPSHOT`：Java 21 产物。

三个产物的 39 个公开类、构造器、方法与 JVM descriptor 经 `javap -public -s` 比较，输出完全相同。验证脚本分别使用实际 JDK 17 / 21 加载新坐标，并与旧坐标在 JDK 21 上的行为比较。探针源码见 [ZxingCompatibilityProbe.java](../../scripts/dependency_alignment/ZxingCompatibilityProbe.java)。

| 输入 | 解码 | 尺寸 | 格式 |
|---|---|---|---|
| `ddd4j-jdk-parity` | 与输入一致 | 256 × 256 | image/png |
| `中文二维码-2026` | 与输入一致 | 256 × 256 | image/png |
| `https://example.invalid/path?a=1` | 与输入一致 | 256 × 256 | image/png |

所用公共依赖来自本次三线 effective POM：ZXing core 3.5.4、Thumbnailator 0.4.21、SLF4J 2.0.18、Commons Lang3 和 Commons IO。探针仅测试二维码编码解码，不宣称验证所有图形、logo、frame 组合。

因此 JDK 17/21 线统一选择真实可用的 `io.github.easy4j` 2.0.x 产物，旧 `io.github.hiwepy` 坐标记录为迁移关系。此次未重新构建或发布上游 SDK，也未虚构 3.0.x 新坐标的可用性。
