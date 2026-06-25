package io.ddd4j.boot.ddd.config;

import lombok.Data;
import lombok.Getter;

/**
 * ddd4j-ddd 配置属性。
 *
 * <p>对应 {@code application.yml} 中的 {@code ddd4j.ddd} 前缀：
 * <pre>
 * ddd4j:
 *   ddd:
 *     event-store:
 *       type: mem  # mem（内存版，开发测试）| kurrent（生产环境，需自行注入 EventStore Bean）
 * </pre>
 *
 * @author wandl
 * @since 3.4.x
 */
@Data
// @ConfigurationProperties(prefix = "ddd4j.ddd")
public class DddProperties {

    /** 事件存储配置 */
    private EventStoreConfig eventStore = new EventStoreConfig();

    /**
     * 事件存储配置。
     */
    @Data
    public static class EventStoreConfig {

        /**
         * 事件存储类型。
         * <ul>
         *   <li>{@code mem} — 内存版（默认，开发/测试用，无需部署 EventStoreDB）</li>
         *   <li>{@code kurrent} — KurrentDB/EventStoreDB（生产环境，需自行注入 {@code EventStore} Bean）</li>
         * </ul>
         */
        private String type = "mem";

    }

}
