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

import io.ddd4j.extension.monitor.Monitor;
import io.ddd4j.extension.monitor.Sender;
import io.ddd4j.extension.monitor.channel.dingtalk.DingTalkClient;
import io.ddd4j.extension.monitor.channel.dingtalk.DingTalkRobotSender;
import io.ddd4j.extension.monitor.channel.feishu.FeishuClient;
import io.ddd4j.extension.monitor.channel.feishu.FeishuRobotSender;
import io.ddd4j.extension.monitor.channel.wecom.WeComClient;
import io.ddd4j.extension.monitor.channel.wecom.WeComRobotSender;
import io.ddd4j.extension.monitor.runtime.ApplicationStartReporter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link BaseMonitorConfig} 与 {@link Monitor} 门面装配测试。
 *
 * <p>覆盖三件事：
 * <ol>
 *   <li>{@link BaseMonitorProperties} 默认值符合预期（每个通道默认启用、限速默认 null）</li>
 *   <li>{@link BaseMonitorConfig} 工厂方法拿到正确的实现类</li>
 *   <li>工厂链 "properties 配置 → 工厂结果" 正确传递（不是简单的 instance-of 校验）</li>
 *   <li>{@link Monitor} 门面的 {@code ofXxx} 方法也产出一致结果</li>
 * </ol>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class BaseMonitorConfigTest {

    // ---------- 1. 默认值断言 ----------

    /**
     * 验证<code>properties.getLog().isEnable()</code>为真，<code>properties.getLog().getRateLimiterPermitsPerSecond()</code>为空，<code>properties.getLog().getApp().getName()</code>为空。
     */
    @Test
    void defaultPropertiesShouldKeepMonitorDefaults() {
        BaseMonitorProperties properties = new BaseMonitorProperties();

        // 顶层默认
        assertThat(properties.getLog().isEnable()).isTrue();
        assertThat(properties.getLog().getRateLimiterPermitsPerSecond()).isNull();

        // 钉钉
        assertThat(properties.getLog().getDingtalk().isEnable()).isTrue();
        assertThat(properties.getLog().getDingtalk().getToken()).isEmpty();
        assertThat(properties.getLog().getDingtalk().getSecret()).isEmpty();

        // 企微
        assertThat(properties.getLog().getWecom().isEnable()).isTrue();
        assertThat(properties.getLog().getWecom().getKey()).isEmpty();

        // 飞书
        assertThat(properties.getLog().getFeishu().isEnable()).isTrue();
        assertThat(properties.getLog().getFeishu().getWebhookUrl()).isEmpty();
        assertThat(properties.getLog().getFeishu().getSecret()).isEmpty();

        // 应用信息
        assertThat(properties.getLog().getApp().getProject()).isEmpty();
        assertThat(properties.getLog().getApp().getEnv()).isEmpty();
        assertThat(properties.getLog().getApp().getName()).isEmpty();
    }

    /**
     * 验证<code>properties</code>非空。
     */
    @Test
    void baseMonitorConfigShouldCreateBaseMonitorPropertiesBean() {
        BaseMonitorConfig config = new BaseMonitorConfig();
        BaseMonitorProperties properties = config.baseMonitorProperties();
        assertThat(properties).isNotNull().isInstanceOf(BaseMonitorProperties.class);
    }

    // ---------- 2. 工厂方法 instance-of 断言 ----------

    /**
     * 验证<code>config.dingTalkRobotSender(properties)</code> 的类型为 <code>DingTalkRobotSender.class</code>，<code>config.wecomRobotSender(properties)</code> 的类型为 <code>WeComRobotSender.class</code>，<code>config.feishuRobotSender(properties)</code> 的类型为 <code>FeishuRobotSender.class</code>。
     */
    @Test
    void baseMonitorConfigShouldCreateAllSenderBeans() {
        BaseMonitorConfig config = new BaseMonitorConfig();
        BaseMonitorProperties properties = new BaseMonitorProperties();

        assertThat(config.dingTalkRobotSender(properties)).isInstanceOf(DingTalkRobotSender.class);
        assertThat(config.wecomRobotSender(properties)).isInstanceOf(WeComRobotSender.class);
        assertThat(config.feishuRobotSender(properties)).isInstanceOf(FeishuRobotSender.class);
    }

    // ---------- 3. 工厂链 "properties → 实例字段" 的端到端覆盖 ----------

    /**
     * 验证<code>client.accessToken()</code> 等于 <code>"token-abc"</code>，<code>client.secret()</code> 等于 <code>"secret-def"</code>。
     */
    @Test
    void dingTalkRobotSenderShouldPropagatePropertiesToUnderlyingClient() {
        BaseMonitorProperties p = new BaseMonitorProperties();
        p.getLog().getDingtalk().setToken("token-abc");
        p.getLog().getDingtalk().setSecret("secret-def");

        DingTalkRobotSender sender = new BaseMonitorConfig().dingTalkRobotSender(p);

        // 通过反射拿底层 DingTalkClient 的字段，确认它们与 properties 一致；
        // 也想表达：token/secret 两个 String 透传。
        DingTalkClient client = sender.getClient();
        assertThat(client.accessToken()).isEqualTo("token-abc");
        assertThat(client.secret()).isEqualTo("secret-def");
    }

    /**
     * 验证<code>client.key()</code> 等于 <code>"wechat-key-xyz"</code>。
     */
    @Test
    void wecomRobotSenderShouldPropagateKeyToUnderlyingClient() {
        BaseMonitorProperties p = new BaseMonitorProperties();
        p.getLog().getWecom().setKey("wechat-key-xyz");

        WeComRobotSender sender = new BaseMonitorConfig().wecomRobotSender(p);
        WeComClient client = sender.getClient();

        assertThat(client.key()).isEqualTo("wechat-key-xyz");
    }

    /**
     * 验证<code>client.webhookUrl()</code> 等于 <code>"https://open.feishu.cn/open-apis/bot/v2/hook/abcdef"</code>，<code>client.secret()</code>为空。
     */
    @Test
    void feishuRobotSenderShouldPropagateWebhookUrlAndSecretToUnderlyingClient() {
        BaseMonitorProperties p = new BaseMonitorProperties();
        p.getLog().getFeishu().setWebhookUrl("https://open.feishu.cn/open-apis/bot/v2/hook/abcdef");
        p.getLog().getFeishu().setSecret("");

        FeishuRobotSender sender = new BaseMonitorConfig().feishuRobotSender(p);
        FeishuClient client = sender.getClient();

        assertThat(client.webhookUrl()).isEqualTo("https://open.feishu.cn/open-apis/bot/v2/hook/abcdef");
        assertThat(client.secret()).isEmpty();
    }

    /**
     * 验证<code>reporter</code>非空，<code>reporter.appName()</code> 等于 <code>"from-properties"</code>。
     */
    @Test
    void applicationStartReporterShouldWireAppNameFromProperties() {
        BaseMonitorProperties p = new BaseMonitorProperties();
        p.getLog().getApp().setName("from-properties");

        DingTalkRobotSender sender = Monitor.ofDingTalk("token", "secret");
        ApplicationStartReporter reporter = new BaseMonitorConfig()
                .applicationStartReporter(sender, p.getLog().getApp().getName());

        assertThat(reporter).isNotNull();
        // 字段 appName 注入到 reporter 内（reflective access due to private）
        assertThat(reporter.appName()).isEqualTo("from-properties");
    }

    // ---------- 4. Monitor 门面 ----------

    /**
     * 验证<code>sender</code>非空，<code>sender.getClient().accessToken()</code> 等于 <code>"token"</code>，<code>sender.getClient().secret()</code> 等于 <code>"secret"</code>。
     */
    @Test
    void monitorFacadeOfDingTalkShouldReturnSenderImpl() {
        DingTalkRobotSender sender = Monitor.ofDingTalk("token", "secret");
        assertThat(sender).isNotNull().isInstanceOf(Sender.class);
        assertThat(sender.getClient().accessToken()).isEqualTo("token");
        assertThat(sender.getClient().secret()).isEqualTo("secret");
    }

    /**
     * 验证<code>sender</code>非空，<code>sender.getClient().key()</code> 等于 <code>"key"</code>。
     */
    @Test
    void monitorFacadeOfWeComShouldReturnSenderImpl() {
        WeComRobotSender sender = Monitor.ofWeCom("key");
        assertThat(sender).isNotNull().isInstanceOf(Sender.class);
        assertThat(sender.getClient().key()).isEqualTo("key");
    }

    /**
     * 验证<code>sender</code>非空，<code>sender.getClient().webhookUrl()</code> 等于 <code>"https://open.feishu.cn/open-apis/bot/v2/hook/xxx"</code>，<code>sender.getClient().secret()</code> 等于 <code>"secret"</code>。
     */
    @Test
    void monitorFacadeOfFeishuShouldReturnSenderImpl() {
        FeishuRobotSender sender = Monitor.ofFeishu(
                "https://open.feishu.cn/open-apis/bot/v2/hook/xxx", "secret");
        assertThat(sender).isNotNull().isInstanceOf(Sender.class);
        assertThat(sender.getClient().webhookUrl()).isEqualTo("https://open.feishu.cn/open-apis/bot/v2/hook/xxx");
        assertThat(sender.getClient().secret()).isEqualTo("secret");
    }

    /**
     * 验证<code>reporter</code>非空，<code>reporter.appName()</code> 等于 <code>"my-app"</code>。
     */
    @Test
    void monitorFacadeStartupReporterShouldBuildApplicationStartReporter() {
        DingTalkRobotSender sender = Monitor.ofDingTalk("token", "secret");
        ApplicationStartReporter reporter = Monitor.startupReporter(sender, "my-app");
        assertThat(reporter).isNotNull();
        assertThat(reporter.appName()).isEqualTo("my-app");
    }
}
