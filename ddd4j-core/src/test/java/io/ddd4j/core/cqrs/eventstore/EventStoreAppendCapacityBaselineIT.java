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
 * <p>产出：{@code docs/performance/eventstore-append-baseline.json}（样本数、
 * 均值/中位数/P95/P99、环境说明、与直连 EventStore 的对比）。
 * 手工基线（非 JMH）：先预热后测量、关闭输出干扰；用于版本间回归对比，不用于绝对值宣称。
 * 默认不参与构建（类名 *IT 后缀），以 {@code -Dtest=EventStoreAppendCapacityBaselineIT} 显式运行。
 */
class EventStoreAppendCapacityBaselineIT {

    private static final String AGGREGATE_TYPE = "BaselineOrder";
    private static final int WARMUP_OPS = 500;
    private static final int SAMPLES = 2_000;
    private static final int BATCH_SIZE = 10;

    private static BaselineOrderId id(String value) {
        return new BaselineOrderId(value);
    }

    // ========================= 测量路径 =========================

    private static long percentile(long[] sorted, double p) {
        int index = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
    }

    private static double percentOverhead(double baselineMean, double enabledMean) {
        return baselineMean <= 0 ? 0 : (enabledMean - baselineMean) / baselineMean * 100.0;
    }

    @Test
    void appendCapacityBaselineShouldBeMeasuredAndRecorded() throws IOException {
        // 第一轮全量执行仅作预热（丢弃），消除 JIT 预热次序偏差；第二轮为正式采样
        appendSingle();
        appendBatch();
        repositoryAppend();
        List<Metric> metrics = new ArrayList<>();
        metrics.add(appendSingle());
        metrics.add(appendBatch());
        metrics.add(repositoryAppend());

        String json = renderJson(metrics);
        Path target = resolveDocsPath();
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
     */
    private Metric repositoryAppend() {
        DefaultEventSourcingRepository<BaselineOrder, String> repository = new DefaultEventSourcingRepository<>(
                AGGREGATE_TYPE, BaselineOrder.class, BaselineOrderId::new, store());
        for (int i = 0; i < WARMUP_OPS; i++) {
            repository.update(loadedOrder(repository, "warmup-repo-" + i));
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

    private BaselineOrder loadedOrder(DefaultEventSourcingRepository<BaselineOrder, String> repository, String id) {
        BaselineOrder order = new BaselineOrder(id);
        order.create();
        repository.add(order);
        return repository.read(id);
    }

    // ========================= 统计与渲染 =========================

    private List<DomainEvent<?>> batchEvents(AggregateRootId aggregateId) {
        List<DomainEvent<?>> events = new ArrayList<>(BATCH_SIZE);
        for (int i = 0; i < BATCH_SIZE; i++) {
            events.add(new BaselineEvent(aggregateId));
        }
        return events;
    }

    private InMemoryEventStore store() {
        return new InMemoryEventStore();
    }

    /**
     * 解析仓库根 docs 输出路径（surefire 工作目录为模块目录，仓库根 docs 在其上级）。
     */
    private Path resolveDocsPath() {
        Path moduleDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path repoRoot = moduleDir.endsWith("ddd4j-core") ? moduleDir.getParent() : moduleDir;
        return repoRoot.resolve(Paths.get("docs", "performance", "eventstore-append-baseline.json"));
    }

    /**
     * 渲染 JSON 基线（指标、样本数、均值/中位数/P95/P99、环境说明、启用前对比）。
     */
    private String renderJson(List<Metric> metrics) {
        Metric single = metrics.get(0);
        Metric repository = metrics.get(2);
        double overheadPercent = percentOverhead(single.mean(), repository.mean());
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"name\": \"ddd4j eventstore append capacity baseline\",\n");
        json.append("  \"generatedAt\": \"").append(ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).append("\",\n");
        json.append("  \"branch\": \"feature/1.0.x\",\n");
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
                .append("% 开销（版本跟踪 Map 读写 + pullDomainEvents 解耦 + AggregateRootId 适配），属可接受常量级差异；")
                .append("后续版本以本文件为基线对比，若 overheadPercent 显著漂移需回归排查\"\n");
        json.append("  }\n");
        json.append("}\n");
        return json.toString();
    }

    /**
     * 单个指标：样本数组 + 统计摘要。
     */
    private static final class Metric {

        private final String name;
        private final String unit;
        private final long[] samples;

        private Metric(String name, String unit, long[] samples) {
            this.name = Objects.requireNonNull(name, "name must not be null");
            this.unit = Objects.requireNonNull(unit, "unit must not be null");
            this.samples = Objects.requireNonNull(samples, "samples must not be null");
        }

        private double mean() {
            long total = 0;
            for (long sample : samples) {
                total += sample;
            }
            return (double) total / samples.length;
        }

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

        private static final EntityType TYPE = new StringEntityType(AGGREGATE_TYPE);
        private final String value;

        BaselineOrderId(String value) {
            this.value = value;
        }

        @Override
        public EntityType getType() {
            return TYPE;
        }

        @Override
        public String asString() {
            return value;
        }

        @Override
        public String asTypedString() {
            return TYPE.asString() + ":" + value;
        }

        @Override
        public boolean equals(Object o) {
            return this == o || (o instanceof BaselineOrderId && value.equals(((BaselineOrderId) o).value));
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }

    /**
     * 基线事件。
     */
    public static final class BaselineEvent extends DomainEvent<BaselineOrderId> {

        public BaselineEvent() {
            super();
        }

        BaselineEvent(AggregateRootId id) {
            super(new EntityIdPath(id));
        }
    }

    /**
     * 基线聚合根。
     */
    static final class BaselineOrder extends AggregateRoot<String> {

        private String id;

        public BaselineOrder() {
        }

        BaselineOrder(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        void create() {
            apply(new BaselineEvent(new BaselineOrderId(id)));
        }

        void change() {
            apply(new BaselineEvent(new BaselineOrderId(id)));
        }

        private void onBaselineEvent(BaselineEvent event) {
            this.id = event.getEntityId().asString();
        }
    }
}
