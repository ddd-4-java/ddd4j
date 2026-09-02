package io.ddd4j.mq.test;

import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.spi.MQEventPublisherContract;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.DockerClientFactory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * MQ 发布路径 Testcontainers 冒烟集成测试基类（纯 Spring Framework 风格，Java 8 基线）。
 * <p>
 * 反哺自 javalin 仓库 {@code AbstractMqIntegrationTest} 的模式：把 13 个
 * mq-* 模块 ContainerIT 的公共骨架（{@code MQEventPublisherContract} 注入、
 * Docker 前置条件、{@code ddd4j.mq.*} 公共属性、冒烟事件构造、publish 不抛异常断言）
 * 收敛到基类，broker 差异全部留在子类。</p>
 *
 * <p>子类只需提供：Spring 测试注解与上下文配置、静态容器定义、broker 特定的
 * {@code @DynamicPropertySource} 属性、以及需要额外断言的 broker 客户端校验。
 * 以下钩子可按需覆盖：</p>
 * <ul>
 *   <li>{@link #verifyBrokerClient()} — broker 客户端 Bean 非空校验（默认空实现）</li>
 *   <li>{@link #smokeTopic()} — 冒烟 topic（默认 {@code smoke}；SQS 覆写为 queue URL）</li>
 *   <li>{@link #smokeNamespace()} — 冒烟 namespace（默认 {@code it}；RocketMQ/ONS 覆写为空）</li>
 * </ul>
 *
 * <p>通过 ddd4j-mq 的 test-jar 以 test 依赖供扩展模块引用。</p>
 */
public abstract class AbstractMqContainerIT {

    /** 统一冒烟 topic。 */
    protected static final String SMOKE_TOPIC = "smoke";
    /** 统一冒烟 tag。 */
    protected static final String SMOKE_TAG = "ping";
    /** 统一冒烟租户。 */
    protected static final String SMOKE_TENANT_ID = "tenant-it";
    /** 统一冒烟 namespace。 */
    protected static final String SMOKE_NAMESPACE = "it";

    @Autowired
    protected MQEventPublisherContract mqEventPublisher;

    /**
     * Docker 是否可用（Testcontainers 前置条件）。
     * <p>供 {@code @EnabledIf("io.ddd4j.mq.test.AbstractMqContainerIT#isDockerAvailable")} 引用。</p>
     */
    public static boolean isDockerAvailable() {
        try {
            DockerClientFactory.instance().client();
            return true;
        } catch (Throwable ex) {
            return false;
        }
    }

    /**
     * 注册各 IT 公共的 {@code ddd4j.mq.*} 属性（enabled / broker / namespace）。
     * <p>子类在 {@code @DynamicPropertySource} 方法中首先调用，再补充 broker 特定属性。</p>
     *
     * @param registry  Spring 动态属性注册表
     * @param broker    broker 标识（如 {@code kafka}）
     * @param namespace 冒烟 namespace
     */
    protected static void registerCommonMqProperties(DynamicPropertyRegistry registry, String broker, String namespace) {
        registry.add("ddd4j.mq.enabled", () -> "true");
        registry.add("ddd4j.mq.broker", () -> broker);
        registry.add("ddd4j.mq.namespace", () -> namespace);
    }

    /**
     * broker 客户端 Bean 非空校验钩子（默认无额外校验）。
     */
    protected void verifyBrokerClient() {
    }

    /**
     * 冒烟 topic（默认 {@code smoke}）。
     */
    protected String smokeTopic() {
        return SMOKE_TOPIC;
    }

    /**
     * 冒烟 namespace（默认 {@code it}）。
     */
    protected String smokeNamespace() {
        return SMOKE_NAMESPACE;
    }

    /**
     * 构造统一冒烟事件（topic/tag/tenantId 按 {@link #smokeTopic()} 等钩子取值）。
     */
    protected SmokePublishEvent newSmokeEvent() {
        SmokePublishEvent event = new SmokePublishEvent();
        event.setTopic(smokeTopic());
        event.setTag(SMOKE_TAG);
        event.setTenantId(SMOKE_TENANT_ID);
        return event;
    }

    /**
     * 构造冒烟目的地（topic、统一 tag、namespace）。
     */
    protected MQDestination smokeDestination() {
        return MQDestination.of(smokeTopic(), SMOKE_TAG, smokeNamespace());
    }

    /**
     * 冒烟：发布路径不抛异常。
     */
    @Test
    void publishShouldNotThrow() {
        assertNotNull(mqEventPublisher);
        verifyBrokerClient();

        SmokePublishEvent event = newSmokeEvent();
        MQDestination destination = smokeDestination();
        assertDoesNotThrow(() -> mqEventPublisher.publish(event, destination));
    }
}
