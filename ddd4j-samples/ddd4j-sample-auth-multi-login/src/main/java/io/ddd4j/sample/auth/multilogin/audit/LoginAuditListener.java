package io.ddd4j.sample.auth.multilogin.audit;

import io.ddd4j.sample.auth.multilogin.event.LoginFailedEvent;
import io.ddd4j.sample.auth.multilogin.event.LoginSucceededEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component
public class LoginAuditListener {

    private static final int MAX_EVENTS = 50;

    private final ConcurrentLinkedDeque<Map<String, Object>> events = new ConcurrentLinkedDeque<>();

    @EventListener
    public void on(LoginSucceededEvent event) {
        Map<String, Object> record = baseRecord("LOGIN_SUCCEEDED", event.request().getRealm(), event.request().getLoginId());
        record.put("token", event.token());
        record.put("scene", event.request().getExtra().get("loginScene"));
        record.put("occurredAt", event.occurredAt());
        append(record);
    }

    @EventListener
    public void on(LoginFailedEvent event) {
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
