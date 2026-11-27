/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.mq.lifecycle;

import io.ddd4j.core.health.ReadinessResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 MQ 启动状态和就绪状态快照。
 */
class MQStartupStatusTest {

    /**
     * 验证<code>contributor.check().ready()</code>为真，<code>result.ready()</code>为假，<code>result.details().get("state")</code> 等于 <code>"DEGRADED"</code>。
     */
    @Test
    void mapsReadyAndDegradedStatesToReadiness() {
        MQStartupStatus status = new MQStartupStatus("kafka");
        MQReadinessContributor contributor = new MQReadinessContributor(status);
        status.starting();
        status.ready();

        assertTrue(contributor.check().ready());

        status.degraded(failure(false));
        ReadinessResult result = contributor.check();
        assertFalse(result.ready());
        assertEquals("DEGRADED", result.details().get("state"));
    }

    /**
     * 验证<code>snapshot.state()</code> 等于 <code>MQStartupState.FAILED</code>，<code>snapshot.failures().size()</code> 等于 <code>1</code>，<code>snapshot.failures().add(failure(false))</code> 抛出 <code>UnsupportedOperationException.class</code>。
     */
    @Test
    void exposesImmutableFailureSnapshot() {
        MQStartupStatus status = new MQStartupStatus("rabbit");
        status.failed(failure(true));
        MQStartupStatus.Snapshot snapshot = status.snapshot();

        assertEquals(MQStartupState.FAILED, snapshot.state());
        assertEquals(1, snapshot.failures().size());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.failures().add(failure(false)));
    }

    /**
     * 创建 <code>new MQListenerInitializationFailure</code> 供当前测试路径调用。
     *
     * @param required 用于构造本次返回结果的 <code>boolean</code> 输入
     * @return 当前测试使用的结果，取自 <code>new MQListenerInitializationFailure("kafka", "orders", "billing", "OrderListener#consume", required, "connection refused")</code>
     */
    private MQListenerInitializationFailure failure(boolean required) {
        return new MQListenerInitializationFailure(
                "kafka", "orders", "billing", "OrderListener#consume", required, "connection refused");
    }
}
