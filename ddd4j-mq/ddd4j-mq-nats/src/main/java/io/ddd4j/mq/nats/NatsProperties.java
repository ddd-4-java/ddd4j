package io.ddd4j.mq.nats;

import io.nats.client.Connection;
import io.nats.client.Nats;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * NATS 客户端配置（纯 Java，零 Spring 依赖）。
 *
 * <p>对应 {@code io.nats:jnats} 原生 NATS / JetStream 客户端。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class NatsProperties {

    /** NATS 服务器地址列表（逗号分隔，如 {@code nats://host1:4222,nats://host2:4222}） */
    private String servers = "nats://localhost:4222";
    /** 认证用户名 */
    private String username;
    /** 认证密码 */
    private String password;
    /** 客户端名称 */
    private String connectionName = "ddd4j-nats";
    /** 连接超时（毫秒） */
    private long connectTimeoutMillis = 2000L;

    public String getServers() {
        return servers;
    }

    public void setServers(String servers) {
        this.servers = servers;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConnectionName() {
        return connectionName;
    }

    public void setConnectionName(String connectionName) {
        this.connectionName = connectionName;
    }

    public long getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public void setConnectTimeoutMillis(long connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    /**
     * 创建并打开 NATS {@link Connection}。
     */
    public Connection connect() {
        try {
            io.nats.client.Options.Builder builder = new io.nats.client.Options.Builder()
                    .servers(Objects.requireNonNull(servers, "servers").split(","))
                    .connectionName(connectionName)
                    .connectionTimeout(connectTimeoutMillis);
            if (Objects.nonNull(username) && !io.ddd4j.kit.lang.StrKit.isBlank(username)) {
                builder.userInfo(username, password);
            }
            return Nats.connect(builder.build());
        } catch (Exception ex) {
            throw new IllegalStateException("Open NATS connection failed", ex);
        }
    }
}
