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

    /**
     * 保存消息组件当前启动结果的状态对象。
     */
    private static final MQStartupStatus UNMANAGED = new MQStartupStatus("unmanaged", false);

    /**
     * 当前启动状态所描述的代理名称。
     */
    private final String broker;
    /**
     * 当前生命周期是否启用了受管启动与停止。
     */
    private final boolean managed;
    /**
     * 记录当前启动或注册流程所处的状态。
     */
    private final AtomicReference<MQStartupState> state = new AtomicReference<>(MQStartupState.NEW);
    /**
     * 截至当前启动轮次已记录的监听器初始化失败。
     */
    private final List<MQListenerInitializationFailure> failures = new CopyOnWriteArrayList<>();

    /**
     * 创建受管理的 MQ 启动状态。
     *
     * @param broker Broker 标识
     */
    public MQStartupStatus(String broker) {
        this(broker, true);
    }

    /**
     * 初始化指定代理的启动状态。
     *
     * @param broker  当前代理名称
     * @param managed 当前代理是否采用受管生命周期
     */
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
        /**
         * 生成快照时记录的代理名称。
         */
        private final String broker;
        /**
         * 生成快照时记录的启动状态。
         */
        private final MQStartupState state;
        /**
         * 生成快照时复制的监听器初始化失败列表。
         */
        private final List<MQListenerInitializationFailure> failures;

        /**
         * 创建不可变的代理启动快照并复制失败列表。
         *
         * @param broker   当前代理名称
         * @param state    快照所记录的启动状态
         * @param failures 截至生成快照时的监听器初始化失败
         */
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
