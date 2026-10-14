/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.core.cqrs.saga;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Saga 编排行为契约测试（规格：{@code saga-orchestration}）。
 *
 * <p>覆盖：注册顺序前向执行、失败后按注册逆序补偿、部分失败收敛终态
 * （补偿自身失败不阻断其余补偿）。
 */
class SagaMustTest {

    @Test
    void allStepsSucceedMustCompleteInRegistrationOrderWithoutCompensation() {
        List<String> trace = new ArrayList<>();
        Saga saga = new Saga()
                .step(new SagaStep("reserve-inventory", () -> trace.add("reserve"), () -> trace.add("unreserve")))
                .step(new SagaStep("charge-payment", () -> trace.add("charge"), () -> trace.add("refund")))
                .step(new SagaStep("create-order", () -> trace.add("create"), () -> trace.add("cancel")));

        SagaState state = saga.execute();

        assertThat(state).isEqualTo(SagaState.COMPLETED);
        assertThat(trace).containsExactly("reserve", "charge", "create");
        assertThat(saga.failure()).isNull();
    }

    @Test
    void laterStepFailureMustCompensateExecutedStepsInReverseRegistrationOrder() {
        List<String> trace = new ArrayList<>();
        Saga saga = new Saga()
                .step(new SagaStep("s1", () -> trace.add("do-s1"), () -> trace.add("undo-s1")))
                .step(new SagaStep("s2", () -> trace.add("do-s2"), () -> trace.add("undo-s2")))
                .step(new SagaStep("s3", () -> {
                    trace.add("do-s3");
                    throw new IllegalStateException("payment gateway down");
                }, () -> trace.add("undo-s3")))
                .step(new SagaStep("s4", () -> trace.add("do-s4"), () -> trace.add("undo-s4")));

        SagaState state = saga.execute();

        assertThat(state).isEqualTo(SagaState.COMPENSATED);
        // 前向执行到 s3 失败；s4 未执行；补偿按注册逆序 s2 → s1；s3/s4 无补偿
        assertThat(trace).containsExactly("do-s1", "do-s2", "do-s3", "undo-s2", "undo-s1");
        assertThat(saga.executedCompensations()).containsExactly("s2", "s1");
        assertThat(saga.failure()).isInstanceOf(IllegalStateException.class)
                .hasMessage("payment gateway down");
    }

    @Test
    void compensationFailureMustNotBlockRemainingCompensationsAndConvergeToTerminalState() {
        List<String> trace = new ArrayList<>();
        Saga saga = new Saga()
                .step(new SagaStep("s1", () -> trace.add("do-s1"), () -> trace.add("undo-s1")))
                .step(new SagaStep("s2", () -> trace.add("do-s2"), () -> {
                    trace.add("undo-s2");
                    throw new IllegalStateException("refund service unavailable");
                }))
                .step(new SagaStep("s3", () -> {
                    throw new IllegalStateException("step s3 failed");
                }, () -> trace.add("undo-s3")));

        SagaState state = saga.execute();

        // s2 的补偿抛异常，但 s1 的补偿仍被执行，最终收敛到 FAILED 终态
        assertThat(state).isEqualTo(SagaState.FAILED);
        assertThat(trace).containsExactly("do-s1", "do-s2", "undo-s2", "undo-s1");
        assertThat(saga.executedCompensations()).containsExactly("s2", "s1");
    }

    @Test
    void freshInstanceMustStartFromCleanState() {
        List<String> trace = new ArrayList<>();
        Saga first = new Saga()
                .step(new SagaStep("s1", () -> trace.add("do-s1"), () -> trace.add("undo-s1")))
                .step(new SagaStep("s2", () -> {
                    throw new IllegalStateException("boom");
                }, () -> trace.add("undo-s2")));

        assertThat(first.execute()).isEqualTo(SagaState.COMPENSATED);

        // 新实例从零开始，无残留运行态
        Saga second = new Saga()
                .step(new SagaStep("s1", () -> trace.add("do-s1-again"), () -> trace.add("undo-s1-again")));

        assertThat(second.state()).isEqualTo(SagaState.RUNNING);
        assertThat(second.execute()).isEqualTo(SagaState.COMPLETED);
        assertThat(trace).isEqualTo(Arrays.asList("do-s1", "undo-s1", "do-s1-again"));
    }
}
