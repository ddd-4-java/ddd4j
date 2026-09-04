package io.ddd4j.quarkus.cqrs;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Runnable 暂存容器。
 * <p>
 * 用于在 Quartz Job 和调用方之间传递 {@link Runnable} 任务实例，
 * 以 {@code identity} 为键进行存取。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
final class RunnableHolder {

    private static final ConcurrentMap<String, Runnable> TASKS = new ConcurrentHashMap<>();

    private RunnableHolder() {
    }

    static void put(String identity, Runnable task) {
        TASKS.put(identity, task);
    }

    static Runnable get(String identity) {
        return TASKS.get(identity);
    }

    static void remove(String identity) {
        TASKS.remove(identity);
    }
}
