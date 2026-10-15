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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class ThreadContextTest {

    @AfterEach
    void tearDown() {
        ThreadContext.clear();
    }

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

    @Test
    void shouldClearResourcesWhenReplacementIsNull() {
        ThreadContext.set("tenant-id", "tenant-a");

        ThreadContext.setResources(null);

        assertThat(ThreadContext.getResources()).isEmpty();
    }
}
