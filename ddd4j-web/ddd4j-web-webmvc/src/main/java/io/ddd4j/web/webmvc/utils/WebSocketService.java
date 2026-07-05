package io.ddd4j.web.webmvc.utils;

import io.ddd4j.kit.lang.JsonKit;
import io.ddd4j.spring.context.SpringContext;
import io.ddd4j.web.webmvc.config.BaseWebProperties;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * WebSocket 工具服务。
 * <p>提供 WebSocket 客户端连接管理、消息收发、心跳检测及自动重连功能。</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j(topic = "### BASE-WEB : WebSocketService ###")
public class WebSocketService {

    /** 会话缓存（clientId -> WebSocketSession） */
    private static final Map<String, WebSocketSession> SESSIONS = new ConcurrentHashMap<>();
    /** 连接管理器缓存（clientId -> WebSocketConnectionManager） */
    private static final Map<String, WebSocketConnectionManager> MANAGERS = new ConcurrentHashMap<>();

    /**
     * 建立 WebSocket 连接。
     *
     * @param clientId       客户端标识
     * @param url            连接地址
     * @param onMessage      消息接收回调
     * @param onConnected    连接成功回调
     * @param sendHeartbeat  心跳发送逻辑（返回 true 表示需要自动重连）
     */
    @SneakyThrows
    public void connect(String clientId, String url, Consumer<String> onMessage, Consumer<String> onConnected, Supplier<Boolean> sendHeartbeat) {
        TextWebSocketHandler handler = new TextWebSocketHandler() {
            private final Integer reconnectTime = SpringContext.getBean(BaseWebProperties.class).getWs().getReconnectTime();
            private LocalDateTime livingTime = LocalDateTime.now().plusSeconds(70);
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


    /**
     * 断开 WebSocket 连接。
     *
     * @param clientId 客户端标识
     */
    public void disconnect(String clientId) {
        WebSocketConnectionManager manager = MANAGERS.get(clientId);
        if (Objects.isNull(manager)) {
            return;
        }
        manager.stop();
    }

    /**
     * 发送消息到指定客户端。
     *
     * @param clientId 客户端标识
     * @param message  消息内容
     */
    public void sendMessage(String clientId, Object message) {
        WebSocketSession webSocketSession = SESSIONS.get(clientId);
        if (Objects.nonNull(webSocketSession) && webSocketSession.isOpen()) {
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
