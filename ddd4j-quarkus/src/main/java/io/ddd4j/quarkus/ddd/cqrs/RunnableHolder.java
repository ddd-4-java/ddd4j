package io.ddd4j.quarkus.ddd.cqrs;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Runnable 暂存容器。
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
