package io.ddd4j.tx;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 分布式事务上下文。
 *
 * <p>承载全局事务 ID（XID）、事务状态、业务参数，
 * 作为 {@link TransactionManager} 各方法的入参/出参传递。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * TransactionContext ctx = TransactionContext.builder()
 *     .name("order-payment")
 *     .timeoutMs(60_000)
 *     .businessParam("orderId", "12345")
 *     .businessParam("amount", new BigDecimal("99.99"))
 *     .build();
 * }</pre>
 *
 * @author hiwepy
 * @since 4.0.0
 */
public class TransactionContext {

    private final String xid;
    private final String branchId;
    private final TransactionStatus status;
    private final String name;
    private final int timeoutMs;
    private final Map<String, Object> businessParams;

    private TransactionContext(Builder builder) {
        this.xid = builder.xid;
        this.branchId = builder.branchId;
        this.status = builder.status;
        this.name = builder.name;
        this.timeoutMs = builder.timeoutMs;
        this.businessParams = new ConcurrentHashMap<>(builder.businessParams);
    }

    /**
     * 创建 Builder。
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 创建只包含 xid 的上下文（用于挂起/恢复场景）。
     */
    public static TransactionContext ofXid(String xid) {
        return builder().xid(xid).status(TransactionStatus.ACTIVE).build();
    }

    public String getXid() {
        return xid;
    }

    public String getBranchId() {
        return branchId;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public String getName() {
        return name;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public Map<String, Object> getBusinessParams() {
        return businessParams;
    }

    @SuppressWarnings("unchecked")
    public <T> T getBusinessParam(String key) {
        return (T) this.businessParams.get(key);
    }

    public void setBusinessParam(String key, Object value) {
        this.businessParams.put(key, value);
    }

    @Override
    public String toString() {
        return "TransactionContext{xid='" + xid + "', branchId='" + branchId
                + "', status=" + status + ", name='" + name + "'}";
    }

    /**
     * TransactionContext Builder。
     */
    public static class Builder {
        private String xid;
        private String branchId;
        private TransactionStatus status = TransactionStatus.ACTIVE;
        private String name;
        private int timeoutMs = 60_000;
        private final Map<String, Object> businessParams = new ConcurrentHashMap<>();

        public Builder xid(String xid) {
            this.xid = xid;
            return this;
        }

        public Builder branchId(String branchId) {
            this.branchId = branchId;
            return this;
        }

        public Builder status(TransactionStatus status) {
            this.status = status;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder timeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        public Builder businessParam(String key, Object value) {
            this.businessParams.put(key, value);
            return this;
        }

        public Builder businessParams(Map<String, Object> params) {
            this.businessParams.putAll(params);
            return this;
        }

        public TransactionContext build() {
            Objects.requireNonNull(name, "name must not be null");
            return new TransactionContext(this);
        }
    }
}
