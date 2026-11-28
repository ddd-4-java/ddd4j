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

    /** \u4fdd\u5b58\u6d88\u606f\u7ec4\u4ef6\u5f53\u524d\u542f\u52a8\u7ed3\u679c\u7684\u72b6\u6001\u5bf9\u8c61\u3002 */
    private static final MQStartupStatus UNMANAGED = new MQStartupStatus("unmanaged", false);

    /** \u5f53\u524d\u542f\u52a8\u72b6\u6001\u6240\u63cf\u8ff0\u7684\u4ee3\u7406\u540d\u79f0\u3002 */
    private final String broker;
    /** \u5f53\u524d\u751f\u547d\u5468\u671f\u662f\u5426\u542f\u7528\u4e86\u53d7\u7ba1\u542f\u52a8\u4e0e\u505c\u6b62\u3002 */
    private final boolean managed;
    /** \u8bb0\u5f55\u5f53\u524d\u542f\u52a8\u6216\u6ce8\u518c\u6d41\u7a0b\u6240\u5904\u7684\u72b6\u6001\u3002 */
    private final AtomicReference<MQStartupState> state = new AtomicReference<>(MQStartupState.NEW);
    /** \u622a\u81f3\u5f53\u524d\u542f\u52a8\u8f6e\u6b21\u5df2\u8bb0\u5f55\u7684\u76d1\u542c\u5668\u521d\u59cb\u5316\u5931\u8d25\u3002 */
    private final List<MQListenerInitializationFailure> failures = new CopyOnWriteArrayList<>();

    /**
     * \u521b\u5efa\u53d7\u7ba1\u7406\u7684 MQ \u542f\u52a8\u72b6\u6001\u3002
     * @param broker Broker \u6807\u8bc6
     */
    public MQStartupStatus(String broker) {
        this(broker, true);
    }

    /**
     * \u521d\u59cb\u5316\u6307\u5b9a\u4ee3\u7406\u7684\u542f\u52a8\u72b6\u6001\u3002
     * @param broker \u5f53\u524d\u4ee3\u7406\u540d\u79f0
     * @param managed \u5f53\u524d\u4ee3\u7406\u662f\u5426\u91c7\u7528\u53d7\u7ba1\u751f\u547d\u5468\u671f
     */
    private MQStartupStatus(String broker, boolean managed) {
        this.broker = Objects.requireNonNull(broker, "broker must not be null");
        this.managed = managed;
    }

    /**
     * \u8fd4\u56de\u975e\u6258\u7ba1\u7684\u542f\u52a8\u72b6\u6001\u5b9e\u4f8b\u3002
     * @return \u975e\u6258\u7ba1\u5b9e\u4f8b
     */
    public static MQStartupStatus unmanaged() {
        return UNMANAGED;
    }

    /** \u6807\u8bb0 MQ \u6b63\u5728\u542f\u52a8\uff0c\u5e76\u6e05\u9664\u65e7\u5931\u8d25\u8bb0\u5f55\u3002 */
    public void starting() {
        if (managed) {
            failures.clear();
            state.set(MQStartupState.STARTING);
        }
    }

    /** \u6839\u636e\u5931\u8d25\u8bb0\u5f55\u5c06 MQ \u6807\u8bb0\u4e3a\u5c31\u7eea\u6216\u964d\u7ea7\u3002 */
    public void ready() {
        if (managed) {
            state.set(failures.isEmpty() ? MQStartupState.READY : MQStartupState.DEGRADED);
        }
    }

    /**
     * \u8bb0\u5f55\u76d1\u542c\u5668\u5931\u8d25\u5e76\u5c06 MQ \u6807\u8bb0\u4e3a\u964d\u7ea7\u3002
     * @param failure \u76d1\u542c\u5668\u521d\u59cb\u5316\u5931\u8d25\u4fe1\u606f
     */
    public void degraded(MQListenerInitializationFailure failure) {
        if (managed) {
            failures.add(Objects.requireNonNull(failure, "failure must not be null"));
            state.set(MQStartupState.DEGRADED);
        }
    }

    /**
     * \u8bb0\u5f55\u76d1\u542c\u5668\u5931\u8d25\u5e76\u5c06 MQ \u6807\u8bb0\u4e3a\u5931\u8d25\u3002
     * @param failure \u76d1\u542c\u5668\u521d\u59cb\u5316\u5931\u8d25\u4fe1\u606f
     */
    public void failed(MQListenerInitializationFailure failure) {
        if (managed) {
            failures.add(Objects.requireNonNull(failure, "failure must not be null"));
            state.set(MQStartupState.FAILED);
        }
    }

    /** \u5c06 MQ \u6807\u8bb0\u4e3a\u5df2\u505c\u6b62\u3002 */
    public void stopped() {
        if (managed) {
            state.set(MQStartupState.STOPPED);
        }
    }

    /**
     * \u751f\u6210\u5f53\u524d\u542f\u52a8\u72b6\u6001\u7684\u4e0d\u53ef\u53d8\u5feb\u7167\u3002
     * @return \u542f\u52a8\u72b6\u6001\u5feb\u7167
     */
    public Snapshot snapshot() {
        return new Snapshot(broker, state.get(), failures);
    }

    /**
     * 不可变启动状态快照。
     */
    public static final class Snapshot {
        /** \u751f\u6210\u5feb\u7167\u65f6\u8bb0\u5f55\u7684\u4ee3\u7406\u540d\u79f0\u3002 */
        private final String broker;
        /** \u751f\u6210\u5feb\u7167\u65f6\u8bb0\u5f55\u7684\u542f\u52a8\u72b6\u6001\u3002 */
        private final MQStartupState state;
        /** \u751f\u6210\u5feb\u7167\u65f6\u590d\u5236\u7684\u76d1\u542c\u5668\u521d\u59cb\u5316\u5931\u8d25\u5217\u8868\u3002 */
        private final List<MQListenerInitializationFailure> failures;

        /**
         * \u521b\u5efa\u4e0d\u53ef\u53d8\u7684\u4ee3\u7406\u542f\u52a8\u5feb\u7167\u5e76\u590d\u5236\u5931\u8d25\u5217\u8868\u3002
         * @param broker \u5f53\u524d\u4ee3\u7406\u540d\u79f0
         * @param state \u5feb\u7167\u6240\u8bb0\u5f55\u7684\u542f\u52a8\u72b6\u6001
         * @param failures \u622a\u81f3\u751f\u6210\u5feb\u7167\u65f6\u7684\u76d1\u542c\u5668\u521d\u59cb\u5316\u5931\u8d25
         */
        private Snapshot(String broker, MQStartupState state,
                         List<MQListenerInitializationFailure> failures) {
            this.broker = broker;
            this.state = state;
            this.failures = Collections.unmodifiableList(new ArrayList<>(failures));
        }

        /**
         * \u8fd4\u56deBroker \u6807\u8bc6\u3002
         * @return Broker \u6807\u8bc6
         */
        public String broker() {
            return broker;
        }

        /**
         * \u8fd4\u56de\u542f\u52a8\u72b6\u6001\u3002
         * @return \u542f\u52a8\u72b6\u6001
         */
        public MQStartupState state() {
            return state;
        }

        /**
         * \u8fd4\u56de\u76d1\u542c\u5668\u521d\u59cb\u5316\u5931\u8d25\u5217\u8868\u3002
         * @return \u76d1\u542c\u5668\u521d\u59cb\u5316\u5931\u8d25\u5217\u8868
         */
        public List<MQListenerInitializationFailure> failures() {
            return failures;
        }

        /**
         * \u8fd4\u56deBroker \u6807\u8bc6\u3002
         * @return Broker \u6807\u8bc6
         */
        public String getBroker() {
            return broker;
        }

        /**
         * \u8fd4\u56de\u542f\u52a8\u72b6\u6001\u3002
         * @return \u542f\u52a8\u72b6\u6001
         */
        public MQStartupState getState() {
            return state;
        }

        /**
         * \u8fd4\u56de\u76d1\u542c\u5668\u521d\u59cb\u5316\u5931\u8d25\u5217\u8868\u3002
         * @return \u76d1\u542c\u5668\u521d\u59cb\u5316\u5931\u8d25\u5217\u8868
         */
        public List<MQListenerInitializationFailure> getFailures() {
            return failures;
        }
    }
}
