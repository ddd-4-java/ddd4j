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
package io.ddd4j.vertx.command;

import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.CommandHandler;
import io.ddd4j.core.cqrs.command.DefaultCommandBus;
import io.vertx.core.Vertx;

import java.util.Collection;
import java.util.Objects;

/**
 * {@link DefaultCommandBus} 的 Vert.x 5 装配适配器：命令契约与路由全部复用
 * ddd4j-core（ADR-0004），本类只做两件事——收集执行器＋组装总线，零新框架
 * 抽象（不 override {@code execute}，不暴露 Vert.x {@code Future} 包装，
 * 不提供 Verticle 部署扩展点）。
 *
 * <p>调用方在应用装配期将执行器交给 {@link #create}。{@link DefaultCommandBus}
 * 构造时按命令类型建立路由快照，后续修改候选集合不会影响路由。同一命令类型重复时，
 * 工厂抛出 {@link IllegalStateException}，不会返回部分可用的总线。
 *
 * <p><b>{@link CommandHandler @CommandHandler} 发现（ServiceLoader 风格）</b></p>
 * <p>
 * 发现机制为 <b>ServiceLoader 风格</b>（Vert.x 无容器扫描，JDK SPI 是其
 * 生态的标准装配原语）：执行器实现类以 {@code public} 无参构造注册进集成方
 * 应用的 {@code META-INF/services/io.ddd4j.core.cqrs.command.CommandExecutor}
 * 文件，集成方用 {@code ServiceLoader.load(CommandExecutor.class)} 迭代成
 * 集合供入 {@code create}（工厂吃 {@code Collection} 而非自跑发现——集成方
 * 供入的扫描结果直接接线，发现键与过滤权留在集成方侧）。{@code @CommandHandler}
 * 为声明性元数据，与 {@code supportedCommands()} 的一致性由 SPI 侧约定，
 * 不参与发现（ServiceLoader 的发现键是接口名，非注解）。与
 * {@code SpringCommandBus} 的 {@code getBeansOfType}／{@code HelidonCommandBus}
 * 的 {@code HelidonServiceLoader} 均为「执行器集合的容器原生枚举」等价物，
 * 差异只在发现键。
 *
 * <p><b>同步 execute 刻意不暴露 Vert.x 异步回路</b></p>
 * <p>
 * {@code execute} 继承 {@link DefaultCommandBus} 的<b>同步</b>签名（返回
 * {@code Result}，不包 {@code Future}）：总线内部对执行器的调用是同步分发，
 * 调用发生在调用者线程；事件循环上不得执行阻塞处理器。集成方需要异步语义时把 {@code execute}
 * 委托给 worker 线程（{@code vertx.executeBlocking(...)} 或 Verticle 的
 * worker 部署）自行包装即可，总线不为包装再立抽象（ADR-0004：零新框架抽象）。
 *
 * <p><b>事务边界刻意不在本层（纯分发路由）</b></p>
 * <p>
 * Vert.x 没有事务管理栈——本模块<b>零事务依赖</b>：事务由集成方在<b>自己的
 * 执行器实现／worker 回调</b>上自管（如经 Vert.x SQL client 的
 * {@code withTransaction} 包住命令落库），总线不做 {@code execute} override、
 * 不参与事务边界（适配器只做纯分发路由）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see DefaultCommandBus
 * @see CommandHandler
 * @see Vertx
 * @since 2.0.x
 */
public class VertxCommandBus extends DefaultCommandBus {

    /**
     * 从执行器集合建立命令路由快照。
     *
     * @param executors 执行器集合，非空
     */
    protected VertxCommandBus(Collection<? extends CommandExecutor<?>> executors) {
        super(executors);
    }

    /**
     * 使用调用方提供的 Vert.x 实例和执行器创建同步命令总线。
     *
     * @param vertx     集成方 Vert.x 实例（装配锚点，确保总线绑定真实运行时创建，
     *                  经 {@code Vertx.vertx()} 获取），非空
     * @param executors 集成方经 ServiceLoader／手动扫描供入的执行器候选，非空
     * @return 已完成整批注册的总线
     * @throws IllegalStateException 任一命令类型重复注册（消息含命令类型全限定名）
     */
    public static VertxCommandBus create(Vertx vertx, Collection<CommandExecutor<?>> executors) {
        Objects.requireNonNull(vertx, "vertx must not be null");
        Objects.requireNonNull(executors, "executors must not be null");
        return new VertxCommandBus(executors);
    }
}
