# ddd4j-metrics

投影执行次数、事件数、耗时与异常的 OpenTelemetry 指标。

投影一次运行会记录运行次数，事件数与耗时分别由计数器和直方图承载，失败另有错误计数。改埋点时通过测试中的内存指标读取器查看导出值；同一 streamId 应累加，不同 streamId 需要可区分。修改属性名会影响现有指标查询。

实现入口见 [OpenTelemetryProjectionMetrics](src/main/java/io/ddd4j/metrics/OpenTelemetryProjectionMetrics.java)。

仪表名称引用 core 的 ProjectionConstants。诊断指标丢失时先检查 scope 和 streamId，再查计数器的增量与耗时记录。

## 回归

优先运行 [OpenTelemetryProjectionMetricsTest](src/test/java/io/ddd4j/metrics/OpenTelemetryProjectionMetricsTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-metrics -am -Dtest=OpenTelemetryProjectionMetricsTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-metrics -am verify
```
