/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.mq.lifecycle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * MQ客户端的线程安全启动状态。
 */
public final class MQStartupStatus {

    private static final MQStartupStatus UNMANAGED = new MQStartupStatus("unmanaged", false);

    private final String broker;
    private final boolean managed;
    private final AtomicReference<MQStartupState> state = new AtomicReference<>(MQStartupState.NEW);
    private final List<MQListenerInitializationFailure> failures = new CopyOnWriteArrayList<>();

    /**
     * 创建受管理的 MQ 启动状态。
     *
     * @param broker Broker 标识
     */
    public MQStartupStatus(String broker) {
        this(broker, true);
    }

    private MQStartupStatus(String broker, boolean managed) {
        this.broker = Objects.requireNonNull(broker, "broker must not be null");
        this.managed = managed;
    }

    /**
     * 返回非托管的启动状态实例。
     *
     * @return 非托管实例
     */
    public static MQStartupStatus unmanaged() {
        return UNMANAGED;
    }

    /**
     * 标记 MQ 正在启动，并清除旧失败记录。
     */
    public void starting() {
        if (managed) {
            failures.clear();
            state.set(MQStartupState.STARTING);
        }
    }

    /**
     * 根据失败记录将 MQ 标记为就绪或降级。
     */
    public void ready() {
        if (managed) {
            state.set(failures.isEmpty() ? MQStartupState.READY : MQStartupState.DEGRADED);
        }
    }

    /**
     * 记录监听器失败并将 MQ 标记为降级。
     *
     * @param failure 监听器初始化失败信息
     */
    public void degraded(MQListenerInitializationFailure failure) {
        if (managed) {
            failures.add(Objects.requireNonNull(failure, "failure must not be null"));
            state.set(MQStartupState.DEGRADED);
        }
    }

    /**
     * 记录监听器失败并将 MQ 标记为失败。
     *
     * @param failure 监听器初始化失败信息
     */
    public void failed(MQListenerInitializationFailure failure) {
        if (managed) {
            failures.add(Objects.requireNonNull(failure, "failure must not be null"));
            state.set(MQStartupState.FAILED);
        }
    }

    /**
     * 将 MQ 标记为已停止。
     */
    public void stopped() {
        if (managed) {
            state.set(MQStartupState.STOPPED);
        }
    }

    /**
     * 生成当前启动状态的不可变快照。
     *
     * @return 启动状态快照
     */
    public Snapshot snapshot() {
        return new Snapshot(broker, state.get(), failures);
    }

    /**
     * 不可变启动状态快照。
     */
    public static final class Snapshot {
        private final String broker;
        private final MQStartupState state;
        private final List<MQListenerInitializationFailure> failures;

        private Snapshot(String broker, MQStartupState state,
                         List<MQListenerInitializationFailure> failures) {
            this.broker = broker;
            this.state = state;
            this.failures = Collections.unmodifiableList(new ArrayList<>(failures));
        }

        /**
         * 返回Broker 标识。
         *
         * @return Broker 标识
         */
        public String broker() {
            return broker;
        }

        /**
         * 返回启动状态。
         *
         * @return 启动状态
         */
        public MQStartupState state() {
            return state;
        }

        /**
         * 返回监听器初始化失败列表。
         *
         * @return 监听器初始化失败列表
         */
        public List<MQListenerInitializationFailure> failures() {
            return failures;
        }

        /**
         * 返回Broker 标识。
         *
         * @return Broker 标识
         */
        public String getBroker() {
            return broker;
        }

        /**
         * 返回启动状态。
         *
         * @return 启动状态
         */
        public MQStartupState getState() {
            return state;
        }

        /**
         * 返回监听器初始化失败列表。
         *
         * @return 监听器初始化失败列表
         */
        public List<MQListenerInitializationFailure> getFailures() {
            return failures;
        }
    }
}
