/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.core.cqrs.eventstore;

import io.ddd4j.core.ddd.event.*;
import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.core.ddd.repository.DefaultEventSourcingRepository;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Serializable;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 事件追加关键存储路径容量基线（task 5.5）。
 *
 * <p>测量对象：
 * <ul>
 *   <li>{@code eventstore.append.single}：{@link InMemoryEventStore#append} 单事件追加</li>
 *   <li>{@code eventstore.append.batch}：批量（10 事件/次）追加的单事件摊销耗时</li>
 *   <li>{@code repository.append}：经 {@code DefaultEventSourcingRepository#update} 的
 *       能力启用路径（版本跟踪 + 未提交事件解耦）追加</li>
 * </ul>
 *
 * <p>产出：{@code ddd4j-core/target/three-line-parity/capacity/eventstore-append-baseline.json}（样本数、
 * 均值/中位数/P95/P99、环境说明、与直连 EventStore 的对比）。
 * 手工基线（非 JMH）：先预热后测量、关闭输出干扰；用于版本间回归对比，不用于绝对值宣称。
 * 默认不参与构建（类名 *IT 后缀），以 {@code -Dtest=EventStoreAppendCapacityBaselineIT} 显式运行。
 */
class EventStoreAppendCapacityBaselineIT {

    /**
     * 测试事件流中使用的聚合类型名称。
     */
    private static final String AGGREGATE_TYPE = "BaselineOrder";
    /**
     * 正式收集性能测量样本前执行的预热次数。
     */
    private static final int WARMUP_OPS = 500;
    /**
     * 容量基线统计中需要保留的样本数量。
     */
    private static final int SAMPLES = 2_000;
    /**
     * 用例向读取或写入入口提交的批次大小。
     */
    private static final int BATCH_SIZE = 10;

    /**
     * 创建 <code>new BaselineOrderId</code> 供当前测试路径调用。
     *
     * @param value 当前用例传入的值
     * @return 当前测试使用的结果，取自 <code>new BaselineOrderId(value)</code>
     */
    private static BaselineOrderId id(String value) {
        return new BaselineOrderId(value);
    }

    // ========================= 测量路径 =========================

    /**
     * 通过 <code>sorted[Math.max(0, Math.min(sorted.length - 1, index))]</code> 取得当前测试需要的结果。
     *
     * @param sorted 传给 <code>Math.ceil()</code> 的 <code>long[]</code> 输入
     * @param p      传给 <code>Math.ceil()</code> 的 <code>double</code> 输入
     * @return 当前测试使用的结果，取自 <code>sorted[Math.max(0, Math.min(sorted.length - 1, index))]</code>
     */
    private static long percentile(long[] sorted, double p) {
        int index = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
    }

    /**
     * 以基线平均耗时为分母计算启用能力后的相对开销。
     *
     * @param baselineMean 未开启能力时测得的平均耗时
     * @param enabledMean  开启能力时测得的平均耗时
     * @return 与基线比较的耗时增幅百分比，基线非正值时为零
     */
    private static double percentOverhead(double baselineMean, double enabledMean) {
        return baselineMean <= 0 ? 0 : (enabledMean - baselineMean) / baselineMean * 100.0;
    }

    /**
     * 验证<code>metric.samples.length == SAMPLES</code>为真。
     *
     * @throws IOException 读取或写入测试数据时发生 I/O 错误
     */
    @Test
    void appendCapacityBaselineShouldBeMeasuredAndRecorded() throws IOException {
        String branch = System.getProperty("ddd4j.baseline.branch");
        if (Objects.isNull(branch) || !branch.matches("feature/[123]\\.0\\.x")) {
            throw new IllegalArgumentException("Set -Dddd4j.baseline.branch=feature/{1|2|3}.0.x");
        }
        // 第一轮全量执行仅作预热（丢弃），消除 JIT 预热次序偏差；第二轮为正式采样
        appendSingle();
        appendBatch();
        repositoryAppend();
        List<Metric> metrics = new ArrayList<>();
        metrics.add(appendSingle());
        metrics.add(appendBatch());
        metrics.add(repositoryAppend());

        String json = renderJson(metrics, branch);
        Path target = resolveOutputPath();
        Files.createDirectories(target.getParent());
        try (Writer writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            writer.write(json);
        }
        System.out.println("CAPACITY_BASELINE_WRITTEN=" + target.toAbsolutePath());
        System.out.println(json);
        for (Metric metric : metrics) {
            assertTrue(metric.samples.length == SAMPLES, "sample count must match");
        }
    }

    /**
     * 单事件追加：先建流（不计入测量），再测量向<b>已存在流</b>追加 1 个事件的耗时
     * （与仓储路径的存量流更新同一测量基准，保证对比公平）。
     *
     * @return 单次事件追加时延的统计结果
     */
    private Metric appendSingle() {
        for (int i = 0; i < WARMUP_OPS; i++) {
            InMemoryEventStore warmup = store();
            AggregateRootId warmupId = id("warmup-single-" + i);
            warmup.append(AGGREGATE_TYPE, warmupId,
                    Collections.singletonList(new BaselineEvent(warmupId)), 0L);
            warmup.append(AGGREGATE_TYPE, warmupId,
                    Collections.singletonList(new BaselineEvent(warmupId)), 1L);
        }
        long[] samples = new long[SAMPLES];
        for (int i = 0; i < SAMPLES; i++) {
            InMemoryEventStore store = store();
            AggregateRootId aggregateId = id("single-" + i);
            // 预建流到版本 1（不计入测量），测量存量流上的追加
            store.append(AGGREGATE_TYPE, aggregateId,
                    Collections.singletonList(new BaselineEvent(aggregateId)), 0L);
            long begin = System.nanoTime();
            store.append(AGGREGATE_TYPE, aggregateId,
                    Collections.singletonList(new BaselineEvent(aggregateId)), 1L);
            samples[i] = System.nanoTime() - begin;
        }
        return new Metric("eventstore.append.single.existing-stream", "ns/op", samples);
    }

    /**
     * 批量追加：每次调用追加 10 个事件，指标为单事件摊销耗时。
     *
     * @return 批量追加的单事件摊销时延统计
     */
    private Metric appendBatch() {
        for (int i = 0; i < WARMUP_OPS / 10; i++) {
            store().append(AGGREGATE_TYPE, id("warmup-batch-" + i), batchEvents(id("warmup-batch-" + i)), 0L);
        }
        long[] samples = new long[SAMPLES];
        for (int i = 0; i < SAMPLES; i++) {
            InMemoryEventStore store = store();
            AggregateRootId aggregateId = id("batch-" + i);
            long begin = System.nanoTime();
            store.append(AGGREGATE_TYPE, aggregateId, batchEvents(aggregateId), 0L);
            samples[i] = (System.nanoTime() - begin) / BATCH_SIZE;
        }
        return new Metric("eventstore.append.batch.10", "ns/event", samples);
    }

    /**
     * 能力启用路径：经 DefaultEventSourcingRepository#update 追加（含版本跟踪与未提交事件解耦）。
     *
     * @return 经过仓储追加的时延统计
     */
    private Metric repositoryAppend() {
        DefaultEventSourcingRepository<BaselineOrder, String> repository = new DefaultEventSourcingRepository<>(
                AGGREGATE_TYPE, BaselineOrder.class, BaselineOrderId::new, store());
        for (int i = 0; i < WARMUP_OPS; i++) {
            BaselineOrder order = loadedOrder(repository, "warmup-repo-" + i);
            order.change();
            repository.update(order);
        }
        long[] samples = new long[SAMPLES];
        for (int i = 0; i < SAMPLES; i++) {
            DefaultEventSourcingRepository<BaselineOrder, String> instance =
                    new DefaultEventSourcingRepository<>(AGGREGATE_TYPE, BaselineOrder.class,
                            BaselineOrderId::new, store());
            BaselineOrder order = loadedOrder(instance, "repo-" + i);
            order.change();
            long begin = System.nanoTime();
            instance.update(order);
            samples[i] = System.nanoTime() - begin;
        }
        return new Metric("repository.append.enabled-path", "ns/op", samples);
    }

    /**
     * 通过 <code>repository.read()</code> 取得当前测试需要的结果。
     *
     * @param repository 当前测试访问的仓储实例
     * @param id         当前要查找的测试记录标识
     * @return 当前测试使用的结果，取自 <code>repository.read(id)</code>
     */
    private BaselineOrder loadedOrder(DefaultEventSourcingRepository<BaselineOrder, String> repository, String id) {
        BaselineOrder order = new BaselineOrder(id);
        order.create();
        repository.add(order);
        return repository.read(id);
    }

    // ========================= 统计与渲染 =========================

    /**
     * 读取 <code>events</code> 供当前测试使用。
     *
     * @param aggregateId 用于定位被测聚合的标识
     * @return 当前测试使用的结果，取自 <code>events</code>
     */
    private List<DomainEvent<?>> batchEvents(AggregateRootId aggregateId) {
        List<DomainEvent<?>> events = new ArrayList<>(BATCH_SIZE);
        for (int i = 0; i < BATCH_SIZE; i++) {
            events.add(new BaselineEvent(aggregateId));
        }
        return events;
    }

    /**
     * 创建 <code>new InMemoryEventStore</code> 供当前测试路径调用。
     *
     * @return 当前测试使用的结果，取自 <code>new InMemoryEventStore()</code>
     */
    private InMemoryEventStore store() {
        return new InMemoryEventStore();
    }

    /**
     * 输出到当前模块的构建产物目录，避免测量环境与噪声覆盖共享产品文档。
     *
     * @return 当前模块 target 下的基线报告文件路径
     */
    private Path resolveOutputPath() {
        Path moduleDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        return moduleDir.resolve(Paths.get("target", "three-line-parity", "capacity",
                "eventstore-append-baseline.json"));
    }

    /**
     * 渲染 JSON 基线（指标、样本数、均值/中位数/P95/P99、环境说明、启用前对比）。
     *
     * @param metrics 要写入基线报告的测量结果
     * @param branch  写入基线报告的分支标识
     * @return 带环境和测量统计的 JSON 基线文本
     */
    private String renderJson(List<Metric> metrics, String branch) {
        Metric single = metrics.get(0);
        Metric repository = metrics.get(2);
        double overheadPercent = percentOverhead(single.mean(), repository.mean());
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"name\": \"ddd4j eventstore append capacity baseline\",\n");
        json.append("  \"generatedAt\": \"").append(ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).append("\",\n");
        json.append("  \"branch\": \"")
                .append(branch)
                .append("\",\n");
        json.append("  \"module\": \"ddd4j-core\",\n");
        json.append("  \"methodology\": \"manual nanoTime baseline (warmup ")
                .append(WARMUP_OPS).append(" ops, ").append(SAMPLES)
                .append(" measured samples per metric); for cross-version regression comparison, not absolute claims\",\n");
        json.append("  \"environment\": {\n")
                .append("    \"javaVersion\": \"").append(System.getProperty("java.version")).append("\",\n")
                .append("    \"javaVendor\": \"").append(System.getProperty("java.vendor")).append("\",\n")
                .append("    \"osName\": \"").append(System.getProperty("os.name")).append("\",\n")
                .append("    \"osArch\": \"").append(System.getProperty("os.arch")).append("\",\n")
                .append("    \"availableProcessors\": ").append(Runtime.getRuntime().availableProcessors()).append(",\n")
                .append("    \"maxMemoryBytes\": ").append(Runtime.getRuntime().maxMemory()).append("\n")
                .append("  },\n");
        json.append("  \"metrics\": [\n");
        for (int i = 0; i < metrics.size(); i++) {
            json.append(metrics.get(i).toJson(i < metrics.size() - 1));
        }
        json.append("  ],\n");
        json.append("  \"comparison\": {\n");
        json.append("    \"baseline\": \"eventstore.append.single (direct EventStore, capability-disabled path)\",\n");
        json.append("    \"enabled\": \"repository.append.enabled-path (DefaultEventSourcingRepository, capability-enabled path)\",\n");
        json.append("    \"overheadPercent\": ").append(String.format(Locale.ROOT, "%.1f", overheadPercent)).append(",\n");
        json.append("    \"note\": \"与启用前（直连 EventStore.append）相比，启用 DefaultEventSourcingRepository 后每次追加额外承担")
                .append(String.format(Locale.ROOT, "%.1f", overheadPercent))
                .append("% 观测开销；该手工基线不证明吞吐、复杂度或长期稳定性，显著漂移需单独回归排查\"\n");
        json.append("  }\n");
        json.append("}\n");
        return json.toString();
    }

    /**
     * 单个指标：样本数组 + 统计摘要。
     */
    private static final class Metric {

        /**
         * 当前测试模型用于展示和断言的名称。
         */
        private final String name;
        /**
         * 定时调度、重试或测试等待使用的时间单位。
         */
        private final String unit;
        /**
         * 供参数化比较及回归验证的输入样本集合。
         */
        private final long[] samples;

        /**
         * 设置 <code>name</code>、<code>unit</code>、<code>samples</code>，用于构造 <code>Metric</code> 测试实例。
         *
         * @param name    当前测试指定的对象或属性名称
         * @param unit    时长参数所使用的时间单位
         * @param samples 用于初始化 <code>Metric</code> 的 <code>long[]</code> 值
         */
        private Metric(String name, String unit, long[] samples) {
            this.name = Objects.requireNonNull(name, "name must not be null");
            this.unit = Objects.requireNonNull(unit, "unit must not be null");
            this.samples = Objects.requireNonNull(samples, "samples must not be null");
        }

        /**
         * 通过 <code>(double)total / samples.length</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>(double)total / samples.length</code>
         */
        private double mean() {
            long total = 0;
            for (long sample : samples) {
                total += sample;
            }
            return (double) total / samples.length;
        }

        /**
         * 通过 <code>json.toString()</code> 取得当前测试需要的结果。
         *
         * @param comma 传给 <code>json.append()</code> 的 <code>boolean</code> 输入
         * @return 当前测试使用的结果，取自 <code>json.toString()</code>
         */
        private String toJson(boolean comma) {
            long[] sorted = samples.clone();
            Arrays.sort(sorted);
            StringBuilder json = new StringBuilder();
            json.append("    {\n");
            json.append("      \"name\": \"").append(name).append("\",\n");
            json.append("      \"unit\": \"").append(unit).append("\",\n");
            json.append("      \"sampleCount\": ").append(samples.length).append(",\n");
            json.append("      \"mean\": ").append(String.format(Locale.ROOT, "%.1f", mean())).append(",\n");
            json.append("      \"median\": ").append(percentile(sorted, 50)).append(",\n");
            json.append("      \"p95\": ").append(percentile(sorted, 95)).append(",\n");
            json.append("      \"p99\": ").append(percentile(sorted, 99)).append(",\n");
            json.append("      \"min\": ").append(sorted[0]).append(",\n");
            json.append("      \"max\": ").append(sorted[sorted.length - 1]).append("\n");
            json.append("    }").append(comma ? ",\n" : "\n");
            return json.toString();
        }
    }

    // ========================= 测试夹具 =========================

    /**
     * 基线聚合标识。
     */
    static final class BaselineOrderId implements AggregateRootId, Serializable {

        /**
         * 测试聚合注册和反序列化所用的实体类型标识。
         */
        private static final EntityType TYPE = new StringEntityType(AGGREGATE_TYPE);
        /**
         * 组成测试标识的原始值。
         */
        private final String value;

        /**
         * 设置 <code>value</code>，用于构造 <code>BaselineOrderId</code> 测试实例。
         *
         * @param value 组成测试标识的原始值
         */
        BaselineOrderId(String value) {
            this.value = value;
        }

        /**
         * 返回当前值或标识公开的类型。
         *
         * @return 实际返回<code>TYPE</code>，供调用方取得当前值
         */
        @Override
        public EntityType getType() {
            return TYPE;
        }

        /**
         * 返回聚合或实体标识的业务字符串。
         *
         * @return 实际返回<code>value</code>，供调用方取得当前值
         */
        @Override
        public String asString() {
            return value;
        }

        /**
         * 返回带类型前缀的标识字符串。
         *
         * <p>测试夹具会调用 <code>TYPE.asString()</code>。</p>
         *
         * @return 实际返回<code>TYPE.asString() + ":" + value</code>
         */
        @Override
        public String asTypedString() {
            return TYPE.asString() + ":" + value;
        }

        /**
         * 比较两个对象的值是否相等。
         *
         * @param o 用来比较的另一个对象，允许为空
         * @return 对象满足此实现的相等条件时为真
         */
        @Override
        public boolean equals(Object o) {
            return this == o || (o instanceof BaselineOrderId && value.equals(((BaselineOrderId) o).value));
        }

        /**
         * 计算与对象相等条件一致的哈希值。
         *
         * @return 由参与相等比较的状态计算出的哈希值
         */
        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }

    /**
     * 基线事件。
     */
    public static final class BaselineEvent extends DomainEvent<BaselineOrderId> {

        /**
         * 使用 <code>super()</code> 初始化 <code>BaselineEvent</code> 测试夹具。
         */
        public BaselineEvent() {
            super();
        }

        /**
         * 使用 <code>super()</code> 初始化 <code>BaselineEvent</code> 测试夹具。
         *
         * @param id 当前要查找的测试记录标识
         */
        BaselineEvent(AggregateRootId id) {
            super(new EntityIdPath(id));
        }
    }

    /**
     * 基线聚合根。
     */
    static final class BaselineOrder extends AggregateRoot<String> {

        /**
         * 标识当前测试对象的主键或事件标识。
         */
        private String id;

        /**
         * 创建字段使用默认初始值的 <code>BaselineOrder</code> 测试对象。
         */
        public BaselineOrder() {
        }

        /**
         * 设置 <code>id</code>，用于构造 <code>BaselineOrder</code> 测试实例。
         *
         * @param id 标识当前测试对象的主键或事件标识
         */
        BaselineOrder(String id) {
            this.id = id;
        }

        /**
         * 读取<code>id</code> 字段。
         *
         * @return <code>id</code> 字段
         */
        @Override
        public String id() {
            return id;
        }

        /**
         * 调用 <code>apply()</code> 执行当前夹具操作。
         */
        void create() {
            apply(new BaselineEvent(new BaselineOrderId(id)));
        }

        /**
         * 调用 <code>apply()</code> 执行当前夹具操作。
         */
        void change() {
            apply(new BaselineEvent(new BaselineOrderId(id)));
        }

        /**
         * 根据本次调用更新 <code>this.id = event.getEntityId().asString()</code>，用于后续测试步骤。
         *
         * @param event 交给被测入口处理的测试领域事件
         */
        private void onBaselineEvent(BaselineEvent event) {
            this.id = event.getEntityId().asString();
        }
    }
}
