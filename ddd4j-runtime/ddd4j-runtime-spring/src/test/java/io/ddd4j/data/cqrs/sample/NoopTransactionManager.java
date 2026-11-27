/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.data.cqrs.sample;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/**
 * 无资源事务管理器（集成测试专用，Task 6.3 修复轮）：真实走
 * {@link AbstractPlatformTransactionManager} 的 begin/commit/rollback 生命周期
 * （{@code prepareSynchronization} 会在 begin 时置位
 * {@code TransactionSynchronizationManager.isActualTransactionActive()}），
 * 但不管理任何实际资源——不引 H2/DataSource 即可让
 * {@code TransactionAutoConfiguration} 激活 {@code @Transactional} 代理，
 * 供 {@code SpringCommandBusIT} 验证方法级事务注解真实生效。
 * spring-tx 6.x 已移除旧的 {@code ResourcelessTransactionManager}，此为等价的
 * 最小测试替身（约 20 行，真 TM 语义非 Mockito mock——mock 绕过
 * {@code getTransaction} 真实流程，置不了事务激活标志）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
class NoopTransactionManager extends AbstractPlatformTransactionManager {

    /**
     * 构造供当前测试使用的 <code>NoopTransactionManager</code> 实例。
     */
    NoopTransactionManager() {
        // 默认 SYNCHRONIZATION_ALWAYS：begin 即初始化同步并置活动事务标志
    }

    /**
     * 创建 <code>new Object</code> 供当前测试路径调用。
     *
     * @return 当前测试使用的结果，取自 <code>new Object()</code>
     */
    @Override
    protected Object doGetTransaction() {
        return new Object();
    }

    /**
     * 由 Spring 事务状态记录开始动作，不创建实际事务资源。
     *
     * @param transaction 当前测试事务管理器维护的事务对象
     * @param definition  测试执行所声明的事务配置
     */
    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        // 无资源：事务状态由父类簿记，无需动作
    }

    /**
     * 结束测试占用的事务状态，无外部事务资源需要提交。
     *
     * @param status 当前测试事务的执行状态
     */
    @Override
    protected void doCommit(DefaultTransactionStatus status) {
        // 无资源：提交为空操作
    }

    /**
     * 结束测试占用的事务状态，无外部事务资源需要回滚。
     *
     * @param status 当前测试事务的执行状态
     */
    @Override
    protected void doRollback(DefaultTransactionStatus status) {
        // 无资源：回滚为空操作
    }
}
