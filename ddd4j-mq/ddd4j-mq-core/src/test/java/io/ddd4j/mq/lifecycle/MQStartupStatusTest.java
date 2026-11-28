/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.mq.lifecycle;

import io.ddd4j.core.health.ReadinessResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证 MQ 启动状态和就绪状态快照。 */
class MQStartupStatusTest {

    /** \u9a8c\u8bc1<code>contributor.check().ready()</code>\u4e3a\u771f\uff0c<code>result.ready()</code>\u4e3a\u5047\uff0c<code>result.details().get("state")</code> \u7b49\u4e8e <code>"DEGRADED"</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>snapshot.state()</code> \u7b49\u4e8e <code>MQStartupState.FAILED</code>\uff0c<code>snapshot.failures().size()</code> \u7b49\u4e8e <code>1</code>\uff0c<code>snapshot.failures().add(failure(false))</code> \u629b\u51fa <code>UnsupportedOperationException.class</code>\u3002 */
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
     * \u521b\u5efa <code>new MQListenerInitializationFailure</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @param required \u7528\u4e8e\u6784\u9020\u672c\u6b21\u8fd4\u56de\u7ed3\u679c\u7684 <code>boolean</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new MQListenerInitializationFailure("kafka", "orders", "billing", "OrderListener#consume", required, "connection refused")</code>
     */
    private MQListenerInitializationFailure failure(boolean required) {
        return new MQListenerInitializationFailure(
                "kafka", "orders", "billing", "OrderListener#consume", required, "connection refused");
    }
}
