/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.core.cqrs.uow;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UnitOfWork 事务边界行为契约测试（规格：{@code unit-of-work}）。
 *
 * <p>覆盖：提交生效、边界内失败回滚且原始异常原样传播、终态幂等保护。
 */
class UnitOfWorkMustTest {

    @Test
    void commitMustExecuteRegisteredCommitCallbacksAndReachCommittedState() {
        AtomicInteger applied = new AtomicInteger();
        List<String> trace = new ArrayList<>();
        UnitOfWork uow = UnitOfWork.begin();
        uow.registerCommit(() -> {
            applied.incrementAndGet();
            trace.add("flush-work");
        });
        uow.registerRollback(() -> trace.add("undo-work"));

        uow.commit();

        assertThat(applied.get()).isEqualTo(1);
        assertThat(uow.state()).isEqualTo(UnitOfWork.State.COMMITTED);
        assertThat(trace).containsExactly("flush-work");
    }

    @Test
    void failureInsideBoundaryMustRollBackAndPropagateOriginalException() {
        List<String> trace = new ArrayList<>();
        UnitOfWork uow = UnitOfWork.begin();
        uow.registerCommit(() -> trace.add("flush-work"));
        uow.registerRollback(() -> trace.add("undo-work"));
        IllegalStateException original = new IllegalStateException("constraint violated");

        // execute 模板：成功提交；失败回滚且原始异常原样传播
        assertThatThrownBy(() -> uow.execute(() -> {
            trace.add("begin-work");
            throw original;
        })).isSameAs(original);

        assertThat(trace).containsExactly("begin-work", "undo-work");
        assertThat(uow.state()).isEqualTo(UnitOfWork.State.ROLLED_BACK);
    }

    @Test
    void rollbackCallbackErrorMustNotReplaceOriginalWorkFailure() {
        UnitOfWork uow = UnitOfWork.begin();
        IllegalStateException original = new IllegalStateException("work failed");
        AtomicInteger cleanupCalls = new AtomicInteger();
        uow.registerRollback(() -> {
            throw new AssertionError("rollback failed");
        });
        uow.registerRollback(cleanupCalls::incrementAndGet);

        assertThatThrownBy(() -> uow.execute(() -> {
            throw original;
        })).isSameAs(original);
        assertThat(uow.state()).isEqualTo(UnitOfWork.State.ROLLED_BACK);
        assertThat(cleanupCalls.get()).isEqualTo(1);
    }

    @Test
    void nestedBoundariesKeepTheirOwnCallbacksAndTerminalStates() {
        List<String> trace = new ArrayList<>();
        UnitOfWork outer = UnitOfWork.begin();
        outer.registerCommit(() -> trace.add("outer-commit"));
        outer.registerRollback(() -> trace.add("outer-rollback"));
        UnitOfWork inner = UnitOfWork.begin();
        inner.registerCommit(() -> trace.add("inner-commit"));
        inner.registerRollback(() -> trace.add("inner-rollback"));
        IllegalStateException innerFailure = new IllegalStateException("inner failed");

        String result = outer.execute(() -> {
            assertThatThrownBy(() -> inner.execute(() -> {
                throw innerFailure;
            })).isSameAs(innerFailure);
            assertThat(outer.state()).isEqualTo(UnitOfWork.State.ACTIVE);
            return "outer-result";
        });

        assertThat(result).isEqualTo("outer-result");
        assertThat(inner.state()).isEqualTo(UnitOfWork.State.ROLLED_BACK);
        assertThat(outer.state()).isEqualTo(UnitOfWork.State.COMMITTED);
        assertThat(trace).containsExactly("inner-rollback", "outer-commit");
    }

    @Test
    void executeSuccessPathMustCommitAndReturnValue() {
        UnitOfWork uow = UnitOfWork.begin();
        List<String> trace = new ArrayList<>();
        uow.registerCommit(() -> trace.add("flush-work"));

        String result = uow.execute(() -> "computed");

        assertThat(result).isEqualTo("computed");
        assertThat(trace).containsExactly("flush-work");
        assertThat(uow.state()).isEqualTo(UnitOfWork.State.COMMITTED);
    }

    @Test
    void terminalStateMustRejectRepeatedTerminationWithoutReplayingCallbacks() {
        AtomicInteger rollbacks = new AtomicInteger();
        UnitOfWork uow = UnitOfWork.begin();
        uow.registerRollback(rollbacks::incrementAndGet);

        uow.rollback();
        assertThat(uow.state()).isEqualTo(UnitOfWork.State.ROLLED_BACK);
        assertThat(rollbacks.get()).isEqualTo(1);

        // 终态之后重复 commit/rollback 必须拒绝，且回滚回调不得再次执行
        assertThatThrownBy(uow::rollback).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(uow::commit).isInstanceOf(IllegalStateException.class);
        assertThat(rollbacks.get()).isEqualTo(1);
    }
}
