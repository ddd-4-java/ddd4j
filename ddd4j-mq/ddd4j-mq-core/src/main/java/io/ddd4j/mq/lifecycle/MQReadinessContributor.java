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

/**
 * 将MQ启动状态映射为框架无关Readiness。
 */
public final class MQReadinessContributor implements ReadinessContributor {

    /** \u4fdd\u5b58\u6d88\u606f\u7ec4\u4ef6\u5f53\u524d\u542f\u52a8\u7ed3\u679c\u7684\u72b6\u6001\u5bf9\u8c61\u3002 */
    private final MQStartupStatus status;

    /**
     * \u521b\u5efa\u57fa\u4e8e MQ \u542f\u52a8\u72b6\u6001\u7684\u5c31\u7eea\u68c0\u67e5\u8d21\u732e\u8005\u3002
     * @param status MQ \u542f\u52a8\u72b6\u6001
     */
    public MQReadinessContributor(MQStartupStatus status) {
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    /**
     * check() 方法。
     * @return 方法执行结果。
     */
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
