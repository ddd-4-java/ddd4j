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
package io.ddd4j.extension.monitor.config;

import io.ddd4j.extension.monitor.channel.dingtalk.DingTalkProperties;
import io.ddd4j.extension.monitor.channel.feishu.FeishuProperties;
import io.ddd4j.extension.monitor.channel.wecom.WeComProperties;
import lombok.Data;

/**
 * ddd4j 监控告警统一配置属性。
 *
 * <p>映射配置文件中以 "monitor" 为前缀的配置项；本类为纯 POJO，
 * 在 Spring 环境下可启用 {@code @ConfigurationProperties(prefix = "monitor")} 完成自动绑定。
 *
 * <p>配置前缀示例（application.yml）：
 * <pre>{@code
 * monitor:
 *   log:
 *     enable: true
 *     rateLimiterPermitsPerSecond: 0.5
 *     dingtalk:
 *       enable: true
 *       token: 钉钉机器人access_token
 *       secret: 钉钉机器人加签密钥
 *     wecom:
 *       enable: true
 *       key: 企业微信机器人webhook_key
 *     feishu:
 *       enable: true
 *       webhookUrl: https://open.feishu.cn/open-apis/bot/v2/hook/xxx
 *       secret: 加签密钥（无则留空）
 *     app:
 *       project: my-project
 *       env: prod
 *       name: my-app
 * }</pre>
 *
 * <p>v2.x 重构：去掉了所有 Logback 专属字段（keywordExpression / asyncAppenderQueueSize / …），
 * {@code QiWeiRobot → WeComRobot}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
// @ConfigurationProperties(prefix = "monitor")
@Data
public class BaseMonitorProperties {

    /**
     * 配置前缀，绑定 yml / properties 时使用
     */
    public static final String PREFIX = "monitor";

    private Log log = new Log();

    @Data
    public static class Log {
        /**
         * 是否启用监控模块总开关
         */
        private boolean enable = true;
        /**
         * 机器人发送速度上限（条/秒）。null 表示不限速。
         */
        private Double rateLimiterPermitsPerSecond;

        /**
         * 钉钉机器人配置
         */
        private DingTalkProperties dingtalk = new DingTalkProperties();
        /**
         * 企业微信机器人配置（was {@code qiwei}）
         */
        private WeComProperties wecom = new WeComProperties();
        /**
         * 飞书机器人配置（v2.x 新增）
         */
        private FeishuProperties feishu = new FeishuProperties();

        /**
         * 应用基本信息（用于告警内容上下文）
         */
        private App app = new App();
    }

    @Data
    public static class App {
        /**
         * 项目名称
         */
        private String project = "";
        /**
         * 当前环境
         */
        private String env = "";
        /**
         * 应用名称
         */
        private String name = "";
    }
}
