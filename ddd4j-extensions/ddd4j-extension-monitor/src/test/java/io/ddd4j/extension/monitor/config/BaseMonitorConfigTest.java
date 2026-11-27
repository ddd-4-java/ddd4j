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

    /** \u9a8c\u8bc1<code>properties.getLog().isEnable()</code>\u4e3a\u771f\uff0c<code>properties.getLog().getRateLimiterPermitsPerSecond()</code>\u4e3a\u7a7a\uff0c<code>properties.getLog().getApp().getName()</code>\u4e3a\u7a7a\u3002 */
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

    /** \u9a8c\u8bc1<code>properties</code>\u975e\u7a7a\u3002 */
    @Test
    void baseMonitorConfigShouldCreateBaseMonitorPropertiesBean() {
        BaseMonitorConfig config = new BaseMonitorConfig();
        BaseMonitorProperties properties = config.baseMonitorProperties();
        assertThat(properties).isNotNull().isInstanceOf(BaseMonitorProperties.class);
    }

    // ---------- 2. 工厂方法 instance-of 断言 ----------

    /** \u9a8c\u8bc1<code>config.dingTalkRobotSender(properties)</code> \u7684\u7c7b\u578b\u4e3a <code>DingTalkRobotSender.class</code>\uff0c<code>config.wecomRobotSender(properties)</code> \u7684\u7c7b\u578b\u4e3a <code>WeComRobotSender.class</code>\uff0c<code>config.feishuRobotSender(properties)</code> \u7684\u7c7b\u578b\u4e3a <code>FeishuRobotSender.class</code>\u3002 */
    @Test
    void baseMonitorConfigShouldCreateAllSenderBeans() {
        BaseMonitorConfig config = new BaseMonitorConfig();
        BaseMonitorProperties properties = new BaseMonitorProperties();

        assertThat(config.dingTalkRobotSender(properties)).isInstanceOf(DingTalkRobotSender.class);
        assertThat(config.wecomRobotSender(properties)).isInstanceOf(WeComRobotSender.class);
        assertThat(config.feishuRobotSender(properties)).isInstanceOf(FeishuRobotSender.class);
    }

    // ---------- 3. 工厂链 "properties → 实例字段" 的端到端覆盖 ----------

    /** \u9a8c\u8bc1<code>client.accessToken()</code> \u7b49\u4e8e <code>"token-abc"</code>\uff0c<code>client.secret()</code> \u7b49\u4e8e <code>"secret-def"</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>client.key()</code> \u7b49\u4e8e <code>"wechat-key-xyz"</code>\u3002 */
    @Test
    void wecomRobotSenderShouldPropagateKeyToUnderlyingClient() {
        BaseMonitorProperties p = new BaseMonitorProperties();
        p.getLog().getWecom().setKey("wechat-key-xyz");

        WeComRobotSender sender = new BaseMonitorConfig().wecomRobotSender(p);
        WeComClient client = sender.getClient();

        assertThat(client.key()).isEqualTo("wechat-key-xyz");
    }

    /** \u9a8c\u8bc1<code>client.webhookUrl()</code> \u7b49\u4e8e <code>"https://open.feishu.cn/open-apis/bot/v2/hook/abcdef"</code>\uff0c<code>client.secret()</code>\u4e3a\u7a7a\u3002 */
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

    /** \u9a8c\u8bc1<code>reporter</code>\u975e\u7a7a\uff0c<code>reporter.appName()</code> \u7b49\u4e8e <code>"from-properties"</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>sender</code>\u975e\u7a7a\uff0c<code>sender.getClient().accessToken()</code> \u7b49\u4e8e <code>"token"</code>\uff0c<code>sender.getClient().secret()</code> \u7b49\u4e8e <code>"secret"</code>\u3002 */
    @Test
    void monitorFacadeOfDingTalkShouldReturnSenderImpl() {
        DingTalkRobotSender sender = Monitor.ofDingTalk("token", "secret");
        assertThat(sender).isNotNull().isInstanceOf(Sender.class);
        assertThat(sender.getClient().accessToken()).isEqualTo("token");
        assertThat(sender.getClient().secret()).isEqualTo("secret");
    }

    /** \u9a8c\u8bc1<code>sender</code>\u975e\u7a7a\uff0c<code>sender.getClient().key()</code> \u7b49\u4e8e <code>"key"</code>\u3002 */
    @Test
    void monitorFacadeOfWeComShouldReturnSenderImpl() {
        WeComRobotSender sender = Monitor.ofWeCom("key");
        assertThat(sender).isNotNull().isInstanceOf(Sender.class);
        assertThat(sender.getClient().key()).isEqualTo("key");
    }

    /** \u9a8c\u8bc1<code>sender</code>\u975e\u7a7a\uff0c<code>sender.getClient().webhookUrl()</code> \u7b49\u4e8e <code>"https://open.feishu.cn/open-apis/bot/v2/hook/xxx"</code>\uff0c<code>sender.getClient().secret()</code> \u7b49\u4e8e <code>"secret"</code>\u3002 */
    @Test
    void monitorFacadeOfFeishuShouldReturnSenderImpl() {
        FeishuRobotSender sender = Monitor.ofFeishu(
                "https://open.feishu.cn/open-apis/bot/v2/hook/xxx", "secret");
        assertThat(sender).isNotNull().isInstanceOf(Sender.class);
        assertThat(sender.getClient().webhookUrl()).isEqualTo("https://open.feishu.cn/open-apis/bot/v2/hook/xxx");
        assertThat(sender.getClient().secret()).isEqualTo("secret");
    }

    /** \u9a8c\u8bc1<code>reporter</code>\u975e\u7a7a\uff0c<code>reporter.appName()</code> \u7b49\u4e8e <code>"my-app"</code>\u3002 */
    @Test
    void monitorFacadeStartupReporterShouldBuildApplicationStartReporter() {
        DingTalkRobotSender sender = Monitor.ofDingTalk("token", "secret");
        ApplicationStartReporter reporter = Monitor.startupReporter(sender, "my-app");
        assertThat(reporter).isNotNull();
        assertThat(reporter.appName()).isEqualTo("my-app");
    }
}
