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

    /** \u6d4b\u8bd5\u4e8b\u4ef6\u6d41\u4e2d\u4f7f\u7528\u7684\u805a\u5408\u7c7b\u578b\u540d\u79f0\u3002 */
    private static final String AGGREGATE_TYPE = "BaselineOrder";
    /** \u6b63\u5f0f\u6536\u96c6\u6027\u80fd\u6d4b\u91cf\u6837\u672c\u524d\u6267\u884c\u7684\u9884\u70ed\u6b21\u6570\u3002 */
    private static final int WARMUP_OPS = 500;
    /** \u5bb9\u91cf\u57fa\u7ebf\u7edf\u8ba1\u4e2d\u9700\u8981\u4fdd\u7559\u7684\u6837\u672c\u6570\u91cf\u3002 */
    private static final int SAMPLES = 2_000;
    /** \u7528\u4f8b\u5411\u8bfb\u53d6\u6216\u5199\u5165\u5165\u53e3\u63d0\u4ea4\u7684\u6279\u6b21\u5927\u5c0f\u3002 */
    private static final int BATCH_SIZE = 10;

    /**
     * \u521b\u5efa <code>new BaselineOrderId</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @param value \u5f53\u524d\u7528\u4f8b\u4f20\u5165\u7684\u503c
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new BaselineOrderId(value)</code>
     */
    private static BaselineOrderId id(String value) {
        return new BaselineOrderId(value);
    }

    // ========================= 测量路径 =========================

    /**
     * \u901a\u8fc7 <code>sorted[Math.max(0, Math.min(sorted.length - 1, index))]</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param sorted \u4f20\u7ed9 <code>Math.ceil()</code> \u7684 <code>long[]</code> \u8f93\u5165
     * @param p \u4f20\u7ed9 <code>Math.ceil()</code> \u7684 <code>double</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>sorted[Math.max(0, Math.min(sorted.length - 1, index))]</code>
     */
    private static long percentile(long[] sorted, double p) {
        int index = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
    }

    /**
     * \u4ee5\u57fa\u7ebf\u5e73\u5747\u8017\u65f6\u4e3a\u5206\u6bcd\u8ba1\u7b97\u542f\u7528\u80fd\u529b\u540e\u7684\u76f8\u5bf9\u5f00\u9500\u3002
     * @param baselineMean \u672a\u5f00\u542f\u80fd\u529b\u65f6\u6d4b\u5f97\u7684\u5e73\u5747\u8017\u65f6
     * @param enabledMean \u5f00\u542f\u80fd\u529b\u65f6\u6d4b\u5f97\u7684\u5e73\u5747\u8017\u65f6
     * @return \u4e0e\u57fa\u7ebf\u6bd4\u8f83\u7684\u8017\u65f6\u589e\u5e45\u767e\u5206\u6bd4\uff0c\u57fa\u7ebf\u975e\u6b63\u503c\u65f6\u4e3a\u96f6
     */
    private static double percentOverhead(double baselineMean, double enabledMean) {
        return baselineMean <= 0 ? 0 : (enabledMean - baselineMean) / baselineMean * 100.0;
    }

    /**
     * \u9a8c\u8bc1<code>metric.samples.length == SAMPLES</code>\u4e3a\u771f\u3002
     * @throws IOException \u8bfb\u53d6\u6216\u5199\u5165\u6d4b\u8bd5\u6570\u636e\u65f6\u53d1\u751f I/O \u9519\u8bef
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
     * \u5355\u4e8b\u4ef6\u8ffd\u52a0\uff1a\u5148\u5efa\u6d41\uff08\u4e0d\u8ba1\u5165\u6d4b\u91cf\uff09\uff0c\u518d\u6d4b\u91cf\u5411<b>\u5df2\u5b58\u5728\u6d41</b>\u8ffd\u52a0 1 \u4e2a\u4e8b\u4ef6\u7684\u8017\u65f6
     *  \uff08\u4e0e\u4ed3\u50a8\u8def\u5f84\u7684\u5b58\u91cf\u6d41\u66f4\u65b0\u540c\u4e00\u6d4b\u91cf\u57fa\u51c6\uff0c\u4fdd\u8bc1\u5bf9\u6bd4\u516c\u5e73\uff09\u3002
     *
     * @return \u5355\u6b21\u4e8b\u4ef6\u8ffd\u52a0\u65f6\u5ef6\u7684\u7edf\u8ba1\u7ed3\u679c
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
     * \u6279\u91cf\u8ffd\u52a0\uff1a\u6bcf\u6b21\u8c03\u7528\u8ffd\u52a0 10 \u4e2a\u4e8b\u4ef6\uff0c\u6307\u6807\u4e3a\u5355\u4e8b\u4ef6\u644a\u9500\u8017\u65f6\u3002
     *
     * @return \u6279\u91cf\u8ffd\u52a0\u7684\u5355\u4e8b\u4ef6\u644a\u9500\u65f6\u5ef6\u7edf\u8ba1
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
     * \u80fd\u529b\u542f\u7528\u8def\u5f84\uff1a\u7ecf DefaultEventSourcingRepository#update \u8ffd\u52a0\uff08\u542b\u7248\u672c\u8ddf\u8e2a\u4e0e\u672a\u63d0\u4ea4\u4e8b\u4ef6\u89e3\u8026\uff09\u3002
     *
     * @return \u7ecf\u8fc7\u4ed3\u50a8\u8ffd\u52a0\u7684\u65f6\u5ef6\u7edf\u8ba1
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
     * \u901a\u8fc7 <code>repository.read()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param repository \u5f53\u524d\u6d4b\u8bd5\u8bbf\u95ee\u7684\u4ed3\u50a8\u5b9e\u4f8b
     * @param id \u5f53\u524d\u8981\u67e5\u627e\u7684\u6d4b\u8bd5\u8bb0\u5f55\u6807\u8bc6
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>repository.read(id)</code>
     */
    private BaselineOrder loadedOrder(DefaultEventSourcingRepository<BaselineOrder, String> repository, String id) {
        BaselineOrder order = new BaselineOrder(id);
        order.create();
        repository.add(order);
        return repository.read(id);
    }

    // ========================= 统计与渲染 =========================

    /**
     * \u8bfb\u53d6 <code>events</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @param aggregateId \u7528\u4e8e\u5b9a\u4f4d\u88ab\u6d4b\u805a\u5408\u7684\u6807\u8bc6
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>events</code>
     */
    private List<DomainEvent<?>> batchEvents(AggregateRootId aggregateId) {
        List<DomainEvent<?>> events = new ArrayList<>(BATCH_SIZE);
        for (int i = 0; i < BATCH_SIZE; i++) {
            events.add(new BaselineEvent(aggregateId));
        }
        return events;
    }

    /**
     * \u521b\u5efa <code>new InMemoryEventStore</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new InMemoryEventStore()</code>
     */
    private InMemoryEventStore store() {
        return new InMemoryEventStore();
    }

    /**
     * \u8f93\u51fa\u5230\u5f53\u524d\u6a21\u5757\u7684\u6784\u5efa\u4ea7\u7269\u76ee\u5f55\uff0c\u907f\u514d\u6d4b\u91cf\u73af\u5883\u4e0e\u566a\u58f0\u8986\u76d6\u5171\u4eab\u4ea7\u54c1\u6587\u6863\u3002
     *
     * @return \u5f53\u524d\u6a21\u5757 target \u4e0b\u7684\u57fa\u7ebf\u62a5\u544a\u6587\u4ef6\u8def\u5f84
     */
    private Path resolveOutputPath() {
        Path moduleDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        return moduleDir.resolve(Paths.get("target", "three-line-parity", "capacity",
                "eventstore-append-baseline.json"));
    }

    /**
     * \u6e32\u67d3 JSON \u57fa\u7ebf\uff08\u6307\u6807\u3001\u6837\u672c\u6570\u3001\u5747\u503c/\u4e2d\u4f4d\u6570/P95/P99\u3001\u73af\u5883\u8bf4\u660e\u3001\u542f\u7528\u524d\u5bf9\u6bd4\uff09\u3002
     *
     * @param metrics \u8981\u5199\u5165\u57fa\u7ebf\u62a5\u544a\u7684\u6d4b\u91cf\u7ed3\u679c
     * @param branch \u5199\u5165\u57fa\u7ebf\u62a5\u544a\u7684\u5206\u652f\u6807\u8bc6
     * @return \u5e26\u73af\u5883\u548c\u6d4b\u91cf\u7edf\u8ba1\u7684 JSON \u57fa\u7ebf\u6587\u672c
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

        /** \u5f53\u524d\u6d4b\u8bd5\u6a21\u578b\u7528\u4e8e\u5c55\u793a\u548c\u65ad\u8a00\u7684\u540d\u79f0\u3002 */
        private final String name;
        /** \u5b9a\u65f6\u8c03\u5ea6\u3001\u91cd\u8bd5\u6216\u6d4b\u8bd5\u7b49\u5f85\u4f7f\u7528\u7684\u65f6\u95f4\u5355\u4f4d\u3002 */
        private final String unit;
        /** \u4f9b\u53c2\u6570\u5316\u6bd4\u8f83\u53ca\u56de\u5f52\u9a8c\u8bc1\u7684\u8f93\u5165\u6837\u672c\u96c6\u5408\u3002 */
        private final long[] samples;

        /**
         * \u8bbe\u7f6e <code>name</code>\u3001<code>unit</code>\u3001<code>samples</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>Metric</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param name \u5f53\u524d\u6d4b\u8bd5\u6307\u5b9a\u7684\u5bf9\u8c61\u6216\u5c5e\u6027\u540d\u79f0
         * @param unit \u65f6\u957f\u53c2\u6570\u6240\u4f7f\u7528\u7684\u65f6\u95f4\u5355\u4f4d
         * @param samples \u7528\u4e8e\u521d\u59cb\u5316 <code>Metric</code> \u7684 <code>long[]</code> \u503c
         */
        private Metric(String name, String unit, long[] samples) {
            this.name = Objects.requireNonNull(name, "name must not be null");
            this.unit = Objects.requireNonNull(unit, "unit must not be null");
            this.samples = Objects.requireNonNull(samples, "samples must not be null");
        }

        /**
         * \u901a\u8fc7 <code>(double)total / samples.length</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>(double)total / samples.length</code>
         */
        private double mean() {
            long total = 0;
            for (long sample : samples) {
                total += sample;
            }
            return (double) total / samples.length;
        }

        /**
         * \u901a\u8fc7 <code>json.toString()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @param comma \u4f20\u7ed9 <code>json.append()</code> \u7684 <code>boolean</code> \u8f93\u5165
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>json.toString()</code>
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

        /** \u6d4b\u8bd5\u805a\u5408\u6ce8\u518c\u548c\u53cd\u5e8f\u5217\u5316\u6240\u7528\u7684\u5b9e\u4f53\u7c7b\u578b\u6807\u8bc6\u3002 */
        private static final EntityType TYPE = new StringEntityType(AGGREGATE_TYPE);
        /** \u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c\u3002 */
        private final String value;

        /**
         * \u8bbe\u7f6e <code>value</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>BaselineOrderId</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param value \u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c
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

        /** \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>BaselineEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002 */
        public BaselineEvent() {
            super();
        }

        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>BaselineEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param id \u5f53\u524d\u8981\u67e5\u627e\u7684\u6d4b\u8bd5\u8bb0\u5f55\u6807\u8bc6
         */
        BaselineEvent(AggregateRootId id) {
            super(new EntityIdPath(id));
        }
    }

    /**
     * 基线聚合根。
     */
    static final class BaselineOrder extends AggregateRoot<String> {

        /** \u6807\u8bc6\u5f53\u524d\u6d4b\u8bd5\u5bf9\u8c61\u7684\u4e3b\u952e\u6216\u4e8b\u4ef6\u6807\u8bc6\u3002 */
        private String id;

        /** \u521b\u5efa\u5b57\u6bb5\u4f7f\u7528\u9ed8\u8ba4\u521d\u59cb\u503c\u7684 <code>BaselineOrder</code> \u6d4b\u8bd5\u5bf9\u8c61\u3002 */
        public BaselineOrder() {
        }

        /**
         * \u8bbe\u7f6e <code>id</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>BaselineOrder</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param id \u6807\u8bc6\u5f53\u524d\u6d4b\u8bd5\u5bf9\u8c61\u7684\u4e3b\u952e\u6216\u4e8b\u4ef6\u6807\u8bc6
         */
        BaselineOrder(String id) {
            this.id = id;
        }

        /**
         * \u8bfb\u53d6<code>id</code> \u5b57\u6bb5\u3002
         * @return <code>id</code> \u5b57\u6bb5
         */
        @Override
        public String id() {
            return id;
        }

        /** \u8c03\u7528 <code>apply()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002 */
        void create() {
            apply(new BaselineEvent(new BaselineOrderId(id)));
        }

        /** \u8c03\u7528 <code>apply()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002 */
        void change() {
            apply(new BaselineEvent(new BaselineOrderId(id)));
        }

        /**
         * \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>this.id = event.getEntityId().asString()</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002
         * @param event \u4ea4\u7ed9\u88ab\u6d4b\u5165\u53e3\u5904\u7406\u7684\u6d4b\u8bd5\u9886\u57df\u4e8b\u4ef6
         */
        private void onBaselineEvent(BaselineEvent event) {
            this.id = event.getEntityId().asString();
        }
    }
}
