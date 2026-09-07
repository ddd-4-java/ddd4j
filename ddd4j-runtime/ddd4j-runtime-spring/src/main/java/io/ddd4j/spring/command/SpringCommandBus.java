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
package io.ddd4j.spring.command;

import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring 命令总线。
 * <p>
 * 基于框架中立的 ddd4j 命令 SPI 实现，在容器刷新后扫描所有 {@link CommandExecutor} Bean
 * 并构建命令类型到执行器的路由表。与 Quarkus {@code QuarkusCommandBus} 行为对齐。
 *
 * <h3>发现机制</h3>
 * <ul>
 *   <li>扫描容器中所有实现 {@link CommandExecutor} 接口的 Bean</li>
 *   <li>兼容 {@code @io.ddd4j.spring.annotation.CommandExecutor} 注解标记的 Bean
 *       （该注解融合了 {@code @Component}，被扫描的 Bean 同时实现接口即可自动注册）</li>
 * </ul>
 *
 * <h3>事务策略</h3>
 * <p>
 * {@link #execute(Command)} 采用<b>方法级</b> {@link Transactional @Transactional}：
 * Spring 事务切面的属性查找先查被调方法自身、再查其声明类——继承自父类的方法其声明类在父类上，
 * 类级注解对子类继承方法不生效（历史缺陷：类级注解在集成方带事务管理器时静默分发非事务）。
 * 方法级注解落在本类自己的方法上，{@code PlatformTransactionManager} 存在时由 Spring 代理真实生效。
 * 因此本类<b>不可 {@code final}</b>（CGLIB 代理需子类化）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see CommandExecutor
 * @see io.ddd4j.spring.annotation.CommandExecutor
 * @since 3.0.0
 */
@Slf4j
@Component
@SuppressWarnings({"rawtypes", "unchecked"})
public class SpringCommandBus implements CommandBus, SmartInitializingSingleton {

    /**
     * 命令类型到执行器的路由映射
     */
    private final Map<Class<? extends Command>, CommandExecutor<?>> executorMap = new ConcurrentHashMap<>();

    /**
     * Spring 应用上下文
     */
    private final ApplicationContext applicationContext;

    public SpringCommandBus(ApplicationContext applicationContext) {
        Objects.requireNonNull(applicationContext, "applicationContext must not be null");
        this.applicationContext = applicationContext;
    }

    /**
     * 容器刷新后扫描所有 {@link CommandExecutor} Bean 并注册到路由表。
     * <p>
     * 使用 {@link SmartInitializingSingleton} 回调，确保所有单例 Bean 已初始化完毕后再扫描，
     * 避免循环依赖和遗漏延迟初始化的 Bean。
     */
    @Override
    public void afterSingletonsInstantiated() {
        Map<String, CommandExecutor> beans = applicationContext.getBeansOfType(CommandExecutor.class);
        for (CommandExecutor executor : beans.values()) {
            for (Object obj : executor.supportedCommands()) {
                Class<? extends Command> commandType = (Class<? extends Command>) obj;
                CommandExecutor<?> previous = executorMap.putIfAbsent(commandType, executor);
                if (Objects.nonNull(previous)) {
                    throw new IllegalStateException("Multiple executors found for command: " + commandType.getName());
                } else {
                    log.info("注册命令执行器: {} -> {}", commandType.getName(),
                            executor.getClass().getSimpleName());
                }
            }
        }
        log.info("SpringCommandBus 初始化完成，共注册 {} 个执行器", executorMap.size());
    }

    /**
     * 执行命令并返回结果。
     * <p>
     * 必须<b>方法级</b> {@code @Transactional}：Spring 事务切面对继承方法只查方法自身与其声明类，
     * 类级注解对本类的继承方法不生效（见类 javadoc「事务策略」）。
     *
     * @param command 命令对象，非空
     * @param <R>     结果载荷类型
     * @return 执行结果
     * @throws IllegalArgumentException command 为 null
     * @throws IllegalStateException    未找到对应执行器
     */
    @Override
    @Transactional
    public <R> Result<R> execute(Command command) {
        if (Objects.isNull(command)) {
            throw new IllegalArgumentException("Command cannot be null");
        }

        CommandExecutor executor = executorMap.get(command.getClass());
        if (Objects.isNull(executor)) {
            throw new IllegalStateException(
                    "No executor found for command: " + command.getClass().getName()
                            + ". Ensure the executor bean implements CommandExecutor interface "
                            + "and its supportedCommands() includes this command type.");
        }
        return (Result<R>) executor.execute(command);
    }
}
