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
package io.ddd4j.spring.cqrs;

import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.readmodel.InMemoryProjectionPositionRepository;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import io.ddd4j.runtime.testkit.AbstractCqrsRuntimeContractTest;
import io.ddd4j.runtime.testkit.CqrsRuntimeContract;
import io.ddd4j.spring.command.SpringCommandBus;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;

import java.util.concurrent.Executors;

/**
 * Spring 运行时 CQRS 契约测试。
 * <p>
 * 使用轻量级 {@link AnnotationConfigApplicationContext} 手动装配，
 * 不依赖 Spring Boot，与模块现有测试风格一致。
 * <p>
 * 装配方式：
 * <ul>
 *   <li>CommandBus：{@link SpringCommandBus}，通过 Spring 容器自动发现执行器 Bean</li>
 *   <li>ViewManager：{@link SpringJpaViewManager}，依赖 ViewScheduler</li>
 *   <li>ViewScheduler：{@link SpringViewScheduler}，基于 ConcurrentTaskScheduler</li>
 *   <li>PositionRepository：{@link InMemoryProjectionPositionRepository}（避免 JPA 重量级装配）</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
class SpringCqrsRuntimeContractTest extends AbstractCqrsRuntimeContractTest {

    /**
     * 创建测试中用于操作当前框架的契约适配器。
     *
     * @return CQRS 运行时契约
     */
    @Override
    protected CqrsRuntimeContract createContract() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(SpringCqrsTestConfig.class);
        return new CqrsRuntimeContract() {

            @Override
            public String runtimeName() {
                return "spring";
            }

            @Override
            public CommandBus commandBus() {
                return context.getBean(SpringCommandBus.class);
            }

            @Override
            public ViewManager viewManager() {
                return context.getBean(SpringJpaViewManager.class);
            }

            @Override
            public ViewScheduler viewScheduler() {
                return context.getBean(SpringViewScheduler.class);
            }

            @Override
            public ProjectionPositionRepository positionRepository() {
                return context.getBean(InMemoryProjectionPositionRepository.class);
            }

            @Override
            public void close() {
                context.close();
            }
        };
    }

    /**
     * Spring 测试配置：注册 CQRS 组件与测试执行器 Bean。
     */
    @Configuration
    static class SpringCqrsTestConfig {

        /**
         * \u521b\u5efa <code>new SpringCommandBus</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
         * @param context \u5f53\u524d\u6846\u67b6\u4fdd\u5b58\u8bf7\u6c42\u6216\u8fd0\u884c\u65f6\u72b6\u6001\u7684\u4e0a\u4e0b\u6587
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new SpringCommandBus(context)</code>
         */
        @Bean
        public SpringCommandBus springCommandBus(AnnotationConfigApplicationContext context) {
            return new SpringCommandBus(context);
        }

        /**
         * \u521b\u5efa <code>new AbstractCqrsRuntimeContractTest.StubExecutorA</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new AbstractCqrsRuntimeContractTest.StubExecutorA()</code>
         */
        @Bean
        public AbstractCqrsRuntimeContractTest.StubExecutorA stubExecutorA() {
            return new AbstractCqrsRuntimeContractTest.StubExecutorA();
        }

        /**
         * \u521b\u5efa <code>new AbstractCqrsRuntimeContractTest.StubExecutorB</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new AbstractCqrsRuntimeContractTest.StubExecutorB()</code>
         */
        @Bean
        public AbstractCqrsRuntimeContractTest.StubExecutorB stubExecutorB() {
            return new AbstractCqrsRuntimeContractTest.StubExecutorB();
        }

        /**
         * \u521b\u5efa <code>new ConcurrentTaskScheduler</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new ConcurrentTaskScheduler(Executors.newScheduledThreadPool(1))</code>
         */
        @Bean
        public ConcurrentTaskScheduler taskScheduler() {
            return new ConcurrentTaskScheduler(Executors.newScheduledThreadPool(1));
        }

        /**
         * \u521b\u5efa <code>new SpringViewScheduler</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
         * @param taskScheduler \u7528\u4e8e\u6784\u9020\u672c\u6b21\u8fd4\u56de\u7ed3\u679c\u7684 <code>ConcurrentTaskScheduler</code> \u8f93\u5165
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new SpringViewScheduler(taskScheduler)</code>
         */
        @Bean
        public SpringViewScheduler springViewScheduler(ConcurrentTaskScheduler taskScheduler) {
            return new SpringViewScheduler(taskScheduler);
        }

        /**
         * \u521b\u5efa <code>new SpringJpaViewManager</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
         * @param scheduler \u7528\u4e8e\u6784\u9020\u672c\u6b21\u8fd4\u56de\u7ed3\u679c\u7684 <code>SpringViewScheduler</code> \u8f93\u5165
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new SpringJpaViewManager(scheduler)</code>
         */
        @Bean
        public SpringJpaViewManager springJpaViewManager(SpringViewScheduler scheduler) {
            return new SpringJpaViewManager(scheduler);
        }

        /**
         * \u521b\u5efa <code>new InMemoryProjectionPositionRepository</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new InMemoryProjectionPositionRepository()</code>
         */
        @Bean
        public InMemoryProjectionPositionRepository inMemoryProjectionPositionRepository() {
            return new InMemoryProjectionPositionRepository();
        }
    }
}
