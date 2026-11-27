/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.mq.lifecycle;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MQ客户端拥有资源的LIFO、幂等生命周期。
 */
public final class MQClientLifecycle implements AutoCloseable {

    /**
     * 协调消息客户端启动、初始化和关闭的生命周期。
     */
    private static final MQClientLifecycle UNMANAGED = new MQClientLifecycle(false);

    /**
     * 当前生命周期负责关闭并按登记逆序清理的资源栈。
     */
    private final Deque<Resource> resources = new ArrayDeque<>();
    /**
     * 当前实例是否已关闭。
     */
    private final AtomicBoolean closed = new AtomicBoolean();
    /**
     * 当前生命周期是否启用了受管启动与停止。
     */
    private final boolean managed;

    /**
     * 创建负责关闭已注册资源的生命周期管理器。
     */
    public MQClientLifecycle() {
        this(true);
    }

    /**
     * 创建跟踪消息代理资源的生命周期。
     *
     * @param managed 是否启用受管生命周期
     */
    private MQClientLifecycle(boolean managed) {
        this.managed = managed;
    }

    /**
     * 返回不管理资源的生命周期实例。
     *
     * @return 非托管生命周期实例
     */
    public static MQClientLifecycle unmanaged() {
        return UNMANAGED;
    }

    /**
     * 注册关闭动作；若已关闭则立即执行。
     *
     * @param name        资源名称
     * @param closeAction 关闭动作
     */
    public synchronized void register(String name, Runnable closeAction) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(closeAction, "closeAction must not be null");
        if (!managed) {
            return;
        }
        if (closed.get()) {
            closeAction.run();
            return;
        }
        resources.push(new Resource(name, closeAction));
    }

    /**
     * 记录当前资源数量作为回滚点。
     *
     * @return 当前回滚点
     */
    public synchronized int checkpoint() {
        return resources.size();
    }

    /**
     * 关闭回滚点之后注册的资源。
     *
     * @param checkpoint 先前取得的回滚点
     */
    public synchronized void rollback(int checkpoint) {
        if (!managed) {
            return;
        }
        if (checkpoint < 0 || checkpoint > resources.size()) {
            throw new IllegalArgumentException("invalid MQ lifecycle checkpoint: " + checkpoint);
        }
        closeUntil(checkpoint);
    }

    /**
     * 结束当前组件持有的资源。
     *
     * <p>此实现调用 <code>closed.compareAndSet(false, true)</code>。</p>
     */
    @Override
    public synchronized void close() {
        if (!managed || !closed.compareAndSet(false, true)) {
            return;
        }
        closeUntil(0);
    }

    /**
     * 按登记逆序关闭指定检查点之后取得的资源，并汇总关闭异常。
     *
     * @param targetSize 清理后应保留的资源数量
     * @throws IllegalStateException 至少一个资源关闭动作执行失败
     */
    private void closeUntil(int targetSize) {
        IllegalStateException aggregate = null;
        while (resources.size() > targetSize) {
            Resource resource = resources.pop();
            try {
                resource.closeAction.run();
            } catch (RuntimeException exception) {
                if (aggregate == null) {
                    aggregate = new IllegalStateException("Failed to close one or more MQ resources");
                }
                aggregate.addSuppressed(new IllegalStateException(resource.name, exception));
            }
        }
        if (aggregate != null) {
            throw aggregate;
        }
    }

    /**
     * 把消息客户端持有的资源名称与对应的关闭动作绑定。
     */
    private static final class Resource {
        /**
         * 用于关闭失败诊断的资源名称。
         */
        private final String name;
        /**
         * 释放一个已登记消息资源的动作。
         */
        private final Runnable closeAction;

        /**
         * 将资源标识与其释放动作一起登记。
         *
         * @param name        资源在错误诊断中的名称
         * @param closeAction 释放该资源的回调
         */
        private Resource(String name, Runnable closeAction) {
            this.name = name;
            this.closeAction = closeAction;
        }
    }
}
