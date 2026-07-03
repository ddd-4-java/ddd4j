package io.ddd4j.sample.auth.multilogin.audit;

import io.ddd4j.core.auth.event.AuthFailedEvent;
import io.ddd4j.core.auth.event.AuthSucceededEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 登录审计监听器。
 *
 * <p>通过 Spring {@link EventListener} 监听登录成功/失败事件，
 * 在内存中保留最近的事件记录，供审计查询使用。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Component
public class LoginAuditListener {

    /**
     * 最大事件保留数量
     */
    private static final int MAX_EVENTS = 50;

    /**
     * 线程安全的事件队列（最近事件在前）
     */
    private final ConcurrentLinkedDeque<Map<String, Object>> events = new ConcurrentLinkedDeque<>();

    /**
     * 监听登录成功事件。
     *
     * @param event 登录成功事件
     */
    @EventListener
    public void on(AuthSucceededEvent event) {
        Map<String, Object> record = baseRecord("LOGIN_SUCCEEDED", event.request().getRealm(), event.request().getLoginId());
        record.put("token", event.token());
        record.put("scene", event.request().getExtra().get("loginScene"));
        record.put("occurredAt", event.occurredAt());
        append(record);
    }

    /**
     * 监听登录失败事件。
     *
     * @param event 登录失败事件
     */
    @EventListener
    public void on(AuthFailedEvent event) {
        Map<String, Object> record = baseRecord("LOGIN_FAILED", event.request().getRealm(), event.request().getLoginId());
        record.put("reason", event.reason());
        record.put("scene", event.request().getExtra().get("loginScene"));
        record.put("occurredAt", event.occurredAt());
        append(record);
    }

    public List<Map<String, Object>> recentEvents() {
        return new ArrayList<>(events);
    }

    private Map<String, Object> baseRecord(String type, String realm, Object loginId) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("type", type);
        record.put("realm", realm);
        record.put("loginId", loginId);
        return record;
    }

    private void append(Map<String, Object> record) {
        events.addFirst(record);
        while (events.size() > MAX_EVENTS) {
            events.pollLast();
        }
    }
}
