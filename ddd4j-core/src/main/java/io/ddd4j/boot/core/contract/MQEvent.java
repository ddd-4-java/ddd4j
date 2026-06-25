package io.ddd4j.boot.core.contract;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.ddd4j.boot.core.context.SpringContext;
import io.ddd4j.boot.core.context.ThreadContext;
import io.ddd4j.boot.core.contract.constant.ContextConstants;
import lombok.Data;
import org.springframework.context.ApplicationContext;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * MQ事件基类
 */
@Data
public class MQEvent implements Serializable {
    // 消息ID，默认当前时间戳
    protected String msgId;
    // 命名空间
    private String namespace;
    // 主题，配置 ddd4j.mq.default-topic 后无须每次指定
    protected String topic;
    // 标签，只支持单个标签，多标签需要分开发送
    protected String tag;
    // namespace、topic、tag拼接符
    protected String concat;
    // 租户ID，默认从线程上下文获取（外部系统 JSON 常用 tenant_id）
    @JsonAlias("tenant_id")
    protected String tenantId;

    // 策略匹配，supports参数来源于@MQEventListener.supports
    public boolean supports(List<String> supports) {
        return supports.contains(match());
    }

    // 策略匹配项
    public String match() {
        return "*";
    }

    // 发布MQ事件
    public void publish() {
        this.publish(getTopic(), getTag(), getTenantId());
    }

    // 发布MQ事件
    public void publish(String topic) {
        this.publish(topic, getTag(), getTenantId());
    }

    // 发布MQ事件
    public void publish(String topic, String tag) {
        this.publish(topic, tag, getTenantId());
    }

    /**
     * 发布 MQ 事件，委托 Spring 容器中的 {@link MQEventPublisher} Bean。
     *
     * @param topic    主题
     * @param tag      标签
     * @param tenantId 租户 ID
     * @throws IllegalStateException 未找到 {@link MQEventPublisher} Bean 时
     */
    public void publish(String topic, String tag, String tenantId) {
        setTopic(topic);
        if (getTopic() == null) {
            setTopic(SpringContext.getEnv().getProperty("ddd4j.mq.default-topic", "DEFAULT"));
        }
        setTag(tag);
        setTenantId(tenantId != null ? tenantId : ThreadContext.get(ContextConstants.TENANT_ID));
        setMsgId(getMsgId() == null ? String.valueOf(System.currentTimeMillis()) : getMsgId());
        if (!publishViaSpringBean()) {
            throw new IllegalStateException(
                    "MQEventPublisher bean not found; enable ddd4j.mq and add a ddd4j-cmpt-* module");
        }
    }

    /**
     * 通过 Spring 容器发布（{@link MQEventPublisher} Bean）。
     *
     * @return 是否已成功发布
     */
    private boolean publishViaSpringBean() {
        try {
            ApplicationContext context = SpringContext.getApplicationContext();
            if (context == null) {
                return false;
            }
            Map<String, MQEventPublisher> publishers = context.getBeansOfType(MQEventPublisher.class);
            if (publishers.isEmpty()) {
                return false;
            }
            publishers.values().iterator().next().publish(this);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public <T extends MQEvent> T tenantId(String tenantId) {
        this.tenantId = tenantId;
        return (T) this;
    }

}