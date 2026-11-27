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
package io.ddd4j.core.context;

import com.alibaba.ttl.threadpool.TtlExecutors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 <code>ThreadContext</code> 在<code>shouldClearResources</code>等操作中的行为契约。
 */
class ThreadContextTest {

    /**
     * 测试结束后执行 <code>ThreadContext.clear()</code> 完成资源回收或状态恢复。
     */
    @AfterEach
    void tearDown() {
        ThreadContext.clear();
    }

    /**
     * 验证<code>(Object) ThreadContext.get("tenant-id")</code> 等于 <code>"tenant-a"</code>，<code>ThreadContext.getResources()</code> 含对应条目 <code>"tenant-id"</code>、<code>"tenant-a"</code>，<code>childResources.get("shared-value")</code> 指向同一实例 <code>sharedValue</code>。
     *
     * @throws Exception 等待并发操作时失败
     */
    @Test
    void shouldIsolateMutableResourceMapOnPlainChildThread() throws Exception {
        Object sharedValue = new Object();
        ThreadContext.set("tenant-id", "tenant-a");
        ThreadContext.set("shared-value", sharedValue);

        FutureTask<Map<Object, Object>> task = new FutureTask<>(() -> {
            assertThat((Object) ThreadContext.get("tenant-id")).isEqualTo("tenant-a");
            ThreadContext.set("tenant-id", "tenant-b");
            ThreadContext.set("child-only", true);
            return ThreadContext.getResources();
        });
        Thread child = new Thread(task, "thread-context-child");
        ThreadContext.set("parent-only", true);
        child.start();
        Map<Object, Object> childResources = task.get(5, TimeUnit.SECONDS);

        assertThat(ThreadContext.getResources()).containsEntry("tenant-id", "tenant-a")
                .doesNotContainKey("child-only");
        assertThat(childResources).containsEntry("tenant-id", "tenant-b")
                .containsEntry("child-only", true)
                .doesNotContainKey("parent-only");
        assertThat(childResources.get("shared-value")).isSameAs(sharedValue);
    }

    /**
     * 验证<code>(Object) ThreadContext.get("tenant-id")</code> 等于 <code>"request"</code>。
     *
     * @throws Exception 等待并发操作时失败
     */
    @Test
    void shouldRestoreExistingWorkerContextAfterWrappedTaskFails() throws Exception {
        ExecutorService raw = Executors.newSingleThreadExecutor();
        ExecutorService wrapped = TtlExecutors.getTtlExecutorService(raw);
        try {
            raw.submit(() -> ThreadContext.set("tenant-id", "worker")).get(5, TimeUnit.SECONDS);
            ThreadContext.set("tenant-id", "request");
            IllegalStateException failure = new IllegalStateException("worker failure");

            Future<?> failedTask = wrapped.submit(() -> {
                assertThat((Object) ThreadContext.get("tenant-id")).isEqualTo("request");
                try (ThreadContext.Scope ignored = ThreadContext.open()) {
                    ThreadContext.set("tenant-id", "inner");
                    ThreadContext.set("task-only", true);
                    throw failure;
                }
            });

            assertThatThrownBy(() -> failedTask.get(5, TimeUnit.SECONDS)).hasCause(failure);
            assertThat(raw.submit(ThreadContext::getResources).get(5, TimeUnit.SECONDS))
                    .containsOnlyKeys("tenant-id").containsEntry("tenant-id", "worker");
            assertThat((Object) ThreadContext.get("tenant-id")).isEqualTo("request");
        } finally {
            raw.shutdownNow();
        }
    }

    /**
     * 验证<code>childResources.get()</code> 含对应条目 <code>"tenant-id"</code>、<code>"tenant-b"</code>，<code>ThreadContext.getResources()</code> 含对应条目 <code>"tenant-id"</code>、<code>"tenant-a"</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void shouldIsolateMutableResourceMapFromThreadPoolTask() throws Exception {
        ExecutorService executor = TtlExecutors.getTtlExecutorService(Executors.newSingleThreadExecutor());
        try {
            ThreadContext.set("tenant-id", "tenant-a");

            Future<Map<Object, Object>> childResources = executor.submit(() -> {
                ThreadContext.set("tenant-id", "tenant-b");
                ThreadContext.set("child-only", true);
                return ThreadContext.getResources();
            });

            assertThat(childResources.get())
                    .containsEntry("tenant-id", "tenant-b")
                    .containsEntry("child-only", true);
            assertThat(ThreadContext.getResources())
                    .containsEntry("tenant-id", "tenant-a")
                    .doesNotContainKey("child-only");
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 验证<code>firstTenant.get()</code> 等于 <code>"tenant-a"</code>，<code>secondTenant.get()</code> 等于 <code>"tenant-b"</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void shouldCaptureResourcesForEachThreadPoolTask() throws Exception {
        ExecutorService executor = TtlExecutors.getTtlExecutorService(Executors.newSingleThreadExecutor());
        try {
            ThreadContext.set("tenant-id", "tenant-a");
            Future<Object> firstTenant = executor.submit(() -> ThreadContext.get("tenant-id"));

            ThreadContext.set("tenant-id", "tenant-b");
            Future<Object> secondTenant = executor.submit(() -> ThreadContext.get("tenant-id"));

            assertThat(firstTenant.get()).isEqualTo("tenant-a");
            assertThat(secondTenant.get()).isEqualTo("tenant-b");
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 验证<code>(Object) ThreadContext.get("tenant-id")</code> 等于 <code>"tenant-b"</code>，<code>(Object) ThreadContext.get("tenant-id")</code> 等于 <code>"tenant-a"</code>。
     */
    @Test
    void shouldRestoreResourcesAfterNestedScopes() {
        ThreadContext.set("tenant-id", "tenant-a");

        try (ThreadContext.Scope ignored = ThreadContext.open()) {
            ThreadContext.set("tenant-id", "tenant-b");
            try (ThreadContext.Scope nested = ThreadContext.open()) {
                ThreadContext.set("tenant-id", "tenant-c");
            }
            assertThat((Object) ThreadContext.get("tenant-id")).isEqualTo("tenant-b");
        }

        assertThat((Object) ThreadContext.get("tenant-id")).isEqualTo("tenant-a");
    }

    /**
     * 验证<code>(Object) ThreadContext.get("tenant-id")</code> 等于 <code>"tenant-b"</code>，<code>(Object) ThreadContext.get("tenant-id")</code> 等于 <code>"tenant-a"</code>。
     */
    @Test
    void shouldReplaceAndRestoreResourcesForScope() {
        ThreadContext.set("tenant-id", "tenant-a");
        Map<Object, Object> scopedResources = new HashMap<>();
        scopedResources.put("tenant-id", "tenant-b");

        try (ThreadContext.Scope ignored = ThreadContext.open(scopedResources)) {
            assertThat((Object) ThreadContext.get("tenant-id")).isEqualTo("tenant-b");
        }

        assertThat((Object) ThreadContext.get("tenant-id")).isEqualTo("tenant-a");
    }

    /**
     * 验证<code>(Object) ThreadContext.get("tenant-id")</code> 等于 <code>"tenant-a"</code>。
     */
    @Test
    void shouldDefensivelyCopyResourceMaps() {
        Map<Object, Object> resources = new HashMap<>();
        resources.put("tenant-id", "tenant-a");
        ThreadContext.setResources(resources);

        resources.put("tenant-id", "changed-outside");
        Map<Object, Object> snapshot = ThreadContext.getValues();
        snapshot.put("tenant-id", "changed-snapshot");

        assertThat((Object) ThreadContext.get("tenant-id")).isEqualTo("tenant-a");
    }

    /**
     * 验证<code>ThreadContext.getResources()</code>为空。
     */
    @Test
    void shouldClearResourcesWhenReplacementIsNull() {
        ThreadContext.set("tenant-id", "tenant-a");

        ThreadContext.setResources(null);

        assertThat(ThreadContext.getResources()).isEmpty();
    }
}
