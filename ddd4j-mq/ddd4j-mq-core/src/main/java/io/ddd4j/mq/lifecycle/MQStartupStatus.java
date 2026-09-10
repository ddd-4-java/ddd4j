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

/** MQ客户端的线程安全启动状态。 */
public final class MQStartupStatus {

    private static final MQStartupStatus UNMANAGED = new MQStartupStatus("unmanaged", false);

    private final String broker;
    private final boolean managed;
    private final AtomicReference<MQStartupState> state = new AtomicReference<>(MQStartupState.NEW);
    private final List<MQListenerInitializationFailure> failures = new CopyOnWriteArrayList<>();

    public MQStartupStatus(String broker) {
        this(broker, true);
    }

    private MQStartupStatus(String broker, boolean managed) {
        this.broker = Objects.requireNonNull(broker, "broker must not be null");
        this.managed = managed;
    }

    public static MQStartupStatus unmanaged() { return UNMANAGED; }

    public void starting() {
        if (managed) {
            failures.clear();
            state.set(MQStartupState.STARTING);
        }
    }

    public void ready() {
        if (managed) {
            state.set(failures.isEmpty() ? MQStartupState.READY : MQStartupState.DEGRADED);
        }
    }

    public void degraded(MQListenerInitializationFailure failure) {
        if (managed) {
            failures.add(Objects.requireNonNull(failure, "failure must not be null"));
            state.set(MQStartupState.DEGRADED);
        }
    }

    public void failed(MQListenerInitializationFailure failure) {
        if (managed) {
            failures.add(Objects.requireNonNull(failure, "failure must not be null"));
            state.set(MQStartupState.FAILED);
        }
    }

    public void stopped() {
        if (managed) {
            state.set(MQStartupState.STOPPED);
        }
    }

    public Snapshot snapshot() {
        return new Snapshot(broker, state.get(), failures);
    }

    /** 不可变启动状态快照。 */
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

        public String broker() { return broker; }
        public MQStartupState state() { return state; }
        public List<MQListenerInitializationFailure> failures() { return failures; }
        public String getBroker() { return broker; }
        public MQStartupState getState() { return state; }
        public List<MQListenerInitializationFailure> getFailures() { return failures; }
    }
}
