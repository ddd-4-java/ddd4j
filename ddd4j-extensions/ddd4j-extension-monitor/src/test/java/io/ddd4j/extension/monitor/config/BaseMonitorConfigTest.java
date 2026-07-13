package io.ddd4j.extension.monitor.config;

import io.ddd4j.extension.monitor.channel.dingtalk.DingTalkClient;
import io.ddd4j.extension.monitor.channel.dingtalk.DingTalkRobotSender;
import io.ddd4j.extension.monitor.channel.feishu.FeishuClient;
import io.ddd4j.extension.monitor.channel.feishu.FeishuRobotSender;
import io.ddd4j.extension.monitor.channel.wecom.WeComClient;
import io.ddd4j.extension.monitor.channel.wecom.WeComRobotSender;
import io.ddd4j.extension.monitor.core.Monitor;
import io.ddd4j.extension.monitor.core.Sender;
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

    @Test
    void baseMonitorConfigShouldCreateBaseMonitorPropertiesBean() {
        BaseMonitorConfig config = new BaseMonitorConfig();
        BaseMonitorProperties properties = config.baseMonitorProperties();
        assertThat(properties).isNotNull().isInstanceOf(BaseMonitorProperties.class);
    }

    // ---------- 2. 工厂方法 instance-of 断言 ----------

    @Test
    void baseMonitorConfigShouldCreateAllSenderBeans() {
        BaseMonitorConfig config = new BaseMonitorConfig();
        BaseMonitorProperties properties = new BaseMonitorProperties();

        assertThat(config.dingTalkRobotSender(properties)).isInstanceOf(DingTalkRobotSender.class);
        assertThat(config.wecomRobotSender(properties)).isInstanceOf(WeComRobotSender.class);
        assertThat(config.feishuRobotSender(properties)).isInstanceOf(FeishuRobotSender.class);
    }

    // ---------- 3. 工厂链 "properties → 实例字段" 的端到端覆盖 ----------

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

    @Test
    void wecomRobotSenderShouldPropagateKeyToUnderlyingClient() {
        BaseMonitorProperties p = new BaseMonitorProperties();
        p.getLog().getWecom().setKey("wechat-key-xyz");

        WeComRobotSender sender = new BaseMonitorConfig().wecomRobotSender(p);
        WeComClient client = sender.getClient();

        assertThat(client.key()).isEqualTo("wechat-key-xyz");
    }

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

    @Test
    void monitorFacadeOfDingTalkShouldReturnSenderImpl() {
        DingTalkRobotSender sender = Monitor.ofDingTalk("token", "secret");
        assertThat(sender).isNotNull().isInstanceOf(Sender.class);
        assertThat(sender.getClient().accessToken()).isEqualTo("token");
        assertThat(sender.getClient().secret()).isEqualTo("secret");
    }

    @Test
    void monitorFacadeOfWeComShouldReturnSenderImpl() {
        WeComRobotSender sender = Monitor.ofWeCom("key");
        assertThat(sender).isNotNull().isInstanceOf(Sender.class);
        assertThat(sender.getClient().key()).isEqualTo("key");
    }

    @Test
    void monitorFacadeOfFeishuShouldReturnSenderImpl() {
        FeishuRobotSender sender = Monitor.ofFeishu(
                "https://open.feishu.cn/open-apis/bot/v2/hook/xxx", "secret");
        assertThat(sender).isNotNull().isInstanceOf(Sender.class);
        assertThat(sender.getClient().webhookUrl()).isEqualTo("https://open.feishu.cn/open-apis/bot/v2/hook/xxx");
        assertThat(sender.getClient().secret()).isEqualTo("secret");
    }

    @Test
    void monitorFacadeStartupReporterShouldBuildApplicationStartReporter() {
        DingTalkRobotSender sender = Monitor.ofDingTalk("token", "secret");
        ApplicationStartReporter reporter = Monitor.startupReporter(sender, "my-app");
        assertThat(reporter).isNotNull();
        assertThat(reporter.appName()).isEqualTo("my-app");
    }
}
