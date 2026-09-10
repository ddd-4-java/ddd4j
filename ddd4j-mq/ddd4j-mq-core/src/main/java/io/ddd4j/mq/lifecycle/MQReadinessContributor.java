/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.mq.lifecycle;

import io.ddd4j.core.health.ReadinessContributor;
import io.ddd4j.core.health.ReadinessResult;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 将MQ启动状态映射为框架无关Readiness。 */
public final class MQReadinessContributor implements ReadinessContributor {

    private final MQStartupStatus status;

    public MQReadinessContributor(MQStartupStatus status) {
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    @Override
    public ReadinessResult check() {
        MQStartupStatus.Snapshot snapshot = status.snapshot();
        String name = "mq-" + snapshot.broker();
        if (snapshot.state() == MQStartupState.READY) {
            return ReadinessResult.ready(name);
        }
        Map<String, String> details = new LinkedHashMap<>();
        details.put("state", snapshot.state().name());
        details.put("failures", String.valueOf(snapshot.failures().size()));
        if (!snapshot.failures().isEmpty()) {
            details.put("reason", snapshot.failures().get(0).reason());
        }
        return new ReadinessResult(name, false, details);
    }
}
