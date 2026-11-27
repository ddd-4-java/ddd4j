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

/** \u9a8c\u8bc1 <code>ThreadContext</code> \u5728<code>shouldClearResources</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
class ThreadContextTest {

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>ThreadContext.clear()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void tearDown() {
        ThreadContext.clear();
    }

    /**
     * \u9a8c\u8bc1<code>childResources.get()</code> \u542b\u5bf9\u5e94\u6761\u76ee <code>"tenant-id"</code>\u3001<code>"tenant-b"</code>\uff0c<code>ThreadContext.getResources()</code> \u542b\u5bf9\u5e94\u6761\u76ee <code>"tenant-id"</code>\u3001<code>"tenant-a"</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
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
     * \u9a8c\u8bc1<code>firstTenant.get()</code> \u7b49\u4e8e <code>"tenant-a"</code>\uff0c<code>secondTenant.get()</code> \u7b49\u4e8e <code>"tenant-b"</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
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

    /** \u9a8c\u8bc1<code>(Object) ThreadContext.get("tenant-id")</code> \u7b49\u4e8e <code>"tenant-b"</code>\uff0c<code>(Object) ThreadContext.get("tenant-id")</code> \u7b49\u4e8e <code>"tenant-a"</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>(Object) ThreadContext.get("tenant-id")</code> \u7b49\u4e8e <code>"tenant-b"</code>\uff0c<code>(Object) ThreadContext.get("tenant-id")</code> \u7b49\u4e8e <code>"tenant-a"</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>(Object) ThreadContext.get("tenant-id")</code> \u7b49\u4e8e <code>"tenant-a"</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>ThreadContext.getResources()</code>\u4e3a\u7a7a\u3002 */
    @Test
    void shouldClearResourcesWhenReplacementIsNull() {
        ThreadContext.set("tenant-id", "tenant-a");

        ThreadContext.setResources(null);

        assertThat(ThreadContext.getResources()).isEmpty();
    }
}
