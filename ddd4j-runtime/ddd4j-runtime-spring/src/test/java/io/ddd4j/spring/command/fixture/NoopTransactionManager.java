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
package io.ddd4j.spring.command.fixture;

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

    /** \u6784\u9020\u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684 <code>NoopTransactionManager</code> \u5b9e\u4f8b\u3002 */
    NoopTransactionManager() {
        // 默认 SYNCHRONIZATION_ALWAYS：begin 即初始化同步并置活动事务标志
    }

    /**
     * \u521b\u5efa <code>new Object</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new Object()</code>
     */
    @Override
    protected Object doGetTransaction() {
        return new Object();
    }

    /**
     * \u7531 Spring \u4e8b\u52a1\u72b6\u6001\u8bb0\u5f55\u5f00\u59cb\u52a8\u4f5c\uff0c\u4e0d\u521b\u5efa\u5b9e\u9645\u4e8b\u52a1\u8d44\u6e90\u3002
     * @param transaction \u5f53\u524d\u6d4b\u8bd5\u4e8b\u52a1\u7ba1\u7406\u5668\u7ef4\u62a4\u7684\u4e8b\u52a1\u5bf9\u8c61
     * @param definition \u6d4b\u8bd5\u6267\u884c\u6240\u58f0\u660e\u7684\u4e8b\u52a1\u914d\u7f6e
     */
    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        // 无资源：事务状态由父类簿记，无需动作
    }

    /**
     * \u7ed3\u675f\u6d4b\u8bd5\u5360\u7528\u7684\u4e8b\u52a1\u72b6\u6001\uff0c\u65e0\u5916\u90e8\u4e8b\u52a1\u8d44\u6e90\u9700\u8981\u63d0\u4ea4\u3002
     * @param status \u5f53\u524d\u6d4b\u8bd5\u4e8b\u52a1\u7684\u6267\u884c\u72b6\u6001
     */
    @Override
    protected void doCommit(DefaultTransactionStatus status) {
        // 无资源：提交为空操作
    }

    /**
     * \u7ed3\u675f\u6d4b\u8bd5\u5360\u7528\u7684\u4e8b\u52a1\u72b6\u6001\uff0c\u65e0\u5916\u90e8\u4e8b\u52a1\u8d44\u6e90\u9700\u8981\u56de\u6eda\u3002
     * @param status \u5f53\u524d\u6d4b\u8bd5\u4e8b\u52a1\u7684\u6267\u884c\u72b6\u6001
     */
    @Override
    protected void doRollback(DefaultTransactionStatus status) {
        // 无资源：回滚为空操作
    }
}
