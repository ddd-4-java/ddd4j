package io.ddd4j.web.webmvc.utils;

import io.ddd4j.spring.context.SpringContext;
import io.ddd4j.kit.lang.JsonKit;
import io.ddd4j.web.config.BaseWebProperties;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * WebSocket工具
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class WebSocketService {

    private static final Logger log = LoggerFactory.getLogger("### BASE-WEB : WebSocketService ###");
    private static final Map<String, WebSocketSession> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<String, WebSocketConnectionManager> MANAGERS = new ConcurrentHashMap<>();

    @SneakyThrows
    public void connect(String clientId, String url, Consumer<String> onMessage, Consumer<String> onConnected, Supplier<Boolean> sendHeartbeat) {
        TextWebSocketHandler handler = new TextWebSocketHandler() {
            private LocalDateTime livingTime = LocalDateTime.now().plusSeconds(70);
            private final Integer reconnectTime = SpringContext.getBean(BaseWebProperties.class).getWs().getReconnectTime();
            private boolean autoReconnect = false;

            // 处理连接后保存session
            @Override
            public void afterConnectionEstablished(WebSocketSession session) {
                log.info("[{}] connected.", clientId);
                SESSIONS.put(clientId, session);
                onConnected.accept(clientId);
                new Thread(() -> {
                    while (!Thread.currentThread().isInterrupted()) {
                        try {
                            Thread.sleep(60 * 1000);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        if (LocalDateTime.now().isAfter(livingTime.plusSeconds(59))) {
                            autoReconnect = sendHeartbeat.get();
                        }
                        if (LocalDateTime.now().isAfter(livingTime.plusMinutes(reconnectTime))) {
                            disconnect(clientId);
                            break;
                        }
                    }
                }).start();
            }

            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                log.info("[{}] => {}", clientId, message.getPayload());
                try {
                    livingTime = LocalDateTime.now();
                    onMessage.accept(message.getPayload());
                } catch (Exception e) {
                    log.error("[{}]Handle WebSocket message failed!", clientId, e);
                }
            }

            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) {
                log.error("[{}]WebSocket transport error: {}", clientId, exception.getMessage(), exception);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
                super.afterConnectionClosed(session, status);
                log.error("[{}] disconnected", clientId);
                SESSIONS.remove(clientId);
                MANAGERS.remove(clientId);
                if (autoReconnect) {
                    log.info("[{}] reconnecting...", clientId);
                    connect(clientId, url, onMessage, onConnected, sendHeartbeat);
                }
            }
        };
        WebSocketConnectionManager manager = new WebSocketConnectionManager(SpringContext.getBean(WebSocketClient.class), handler, url);
        manager.start();
        MANAGERS.put(clientId, manager);
    }


    public void disconnect(String clientId) {
        WebSocketConnectionManager manager = MANAGERS.get(clientId);
        if (manager == null) return;
        manager.stop();
    }

    public void sendMessage(String clientId, Object message) {
        WebSocketSession webSocketSession = SESSIONS.get(clientId);
        if (webSocketSession != null && webSocketSession.isOpen()) {
            String payload = JsonKit.toJson(message);
            log.info("[{}] <= {}", clientId, payload);
            try {
                webSocketSession.sendMessage(new TextMessage(payload));
            } catch (IOException e) {
                log.error("[{}]WebSocket sendMessage error.", clientId, e);
            }
        } else {
            log.error("[{}]WebSocket is not connected.", clientId);
        }
    }

}