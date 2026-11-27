package io.ddd4j.mq.delivery;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

/**
 * 单次 Outbox 调度的汇总结果。
 */

public final class MQOutboxDispatchResult {

    /** Java 序列化版本标识。 */
    private static final long serialVersionUID = 0L;

    /** \u672c\u8f6e\u6210\u529f\u83b7\u5f97\u79df\u7ea6\u7684\u5f85\u6295\u9012\u8bb0\u5f55\u6570\u3002 */
    private final int claimed;

    /** \u672c\u8f6e\u5df2\u6210\u529f\u53d1\u9001\u5e76\u786e\u8ba4\u7684\u8bb0\u5f55\u6570\u3002 */
    private final int published;

    /** \u53d1\u9001\u5931\u8d25\u4e14\u5df2\u91cd\u65b0\u5b89\u6392\u91cd\u8bd5\u7684\u8bb0\u5f55\u6570\u3002 */
    private final int rescheduled;

    /** \u8fbe\u5230\u7ec8\u6b62\u6761\u4ef6\u5e76\u6807\u8bb0\u4e3a\u6b7b\u4ea1\u7684\u8bb0\u5f55\u6570\u3002 */
    private final int dead;

    /** \u53d1\u9001\u540e\u7684\u5b58\u50a8\u786e\u8ba4\u56e0\u79df\u7ea6\u6216\u72b6\u6001\u53d8\u5316\u672a\u88ab\u63a5\u53d7\u7684\u8bb0\u5f55\u6570\u3002 */
    private final int confirmationLost;

    /**
     * 初始化 <code>MQOutboxDispatchResult</code> 使用的 <code>claimed</code>、<code>published</code>、<code>rescheduled</code>、<code>dead</code>、<code>confirmationLost</code>。
     *
     * @param claimed \u5df2\u9886\u53d6\u6570\u91cf
     * @param published \u5df2\u786e\u8ba4\u53d1\u5e03\u6570\u91cf
     * @param rescheduled \u5df2\u5b89\u6392\u91cd\u8bd5\u6570\u91cf
     * @param dead \u9884\u8ba1\u8fdb\u5165\u6b7b\u4fe1\u6570\u91cf
     * @param confirmationLost broker \u5df2\u63a5\u6536\u4f46\u79df\u7ea6\u786e\u8ba4\u4e22\u5931\u6570\u91cf\uff0c\u540e\u7eed\u53ef\u80fd\u91cd\u590d\u6295\u9012
     */

    @JsonCreator()
    public MQOutboxDispatchResult(@JsonProperty("claimed") int claimed, @JsonProperty("published") int published, @JsonProperty("rescheduled") int rescheduled, @JsonProperty("dead") int dead, @JsonProperty("confirmationLost") int confirmationLost) {
        this.claimed = claimed;
        this.published = published;
        this.rescheduled = rescheduled;
        this.dead = dead;
        this.confirmationLost = confirmationLost;
    }

    /** @return \u5df2\u9886\u53d6\u6570\u91cf */
    @JsonProperty("claimed")
    public int claimed() {
        return claimed;
    }

    /** @return \u5df2\u786e\u8ba4\u53d1\u5e03\u6570\u91cf */
    @JsonProperty("published")
    public int published() {
        return published;
    }

    /** @return \u5df2\u5b89\u6392\u91cd\u8bd5\u6570\u91cf */
    @JsonProperty("rescheduled")
    public int rescheduled() {
        return rescheduled;
    }

    /** @return \u9884\u8ba1\u8fdb\u5165\u6b7b\u4fe1\u6570\u91cf */
    @JsonProperty("dead")
    public int dead() {
        return dead;
    }

    /** @return \u79df\u7ea6\u786e\u8ba4\u4e22\u5931\u6570\u91cf */
    @JsonProperty("confirmationLost")
    public int confirmationLost() {
        return confirmationLost;
    }

    /**
     * 执行 <code>equals</code>，调用 <code>obj.getClass</code>。
     *
     * @param obj 传入此操作的 <code>Object</code> 类型 <code>obj</code>
     * @return 按条件返回 <code>true</code>、<code>false</code>
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (Objects.isNull(obj) || getClass() != obj.getClass()) {
            return false;
        }
        MQOutboxDispatchResult other = (MQOutboxDispatchResult) obj;
        return this.claimed == other.claimed && this.published == other.published && this.rescheduled == other.rescheduled && this.dead == other.dead && this.confirmationLost == other.confirmationLost;
    }

    /**
     * 计算与对象相等条件一致的哈希值。
     *
     * @return 由参与相等比较的状态计算出的哈希值
     */
    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Integer.hashCode(claimed);
        result = 31 * result + Integer.hashCode(published);
        result = 31 * result + Integer.hashCode(rescheduled);
        result = 31 * result + Integer.hashCode(dead);
        result = 31 * result + Integer.hashCode(confirmationLost);
        return result;
    }

    /**
     * 生成用于调试和日志的对象描述，包含<code>claimed</code>、<code>published</code>、<code>rescheduled</code>、<code>dead</code>、<code>confirmationLost</code>。
     *
     * @return 表示当前对象状态的字符串
     */
    @Override
    public String toString() {
        return "MQOutboxDispatchResult[claimed=" + claimed + ", published=" + published + ", rescheduled=" + rescheduled + ", dead=" + dead + ", confirmationLost=" + confirmationLost + "]";
    }
}
