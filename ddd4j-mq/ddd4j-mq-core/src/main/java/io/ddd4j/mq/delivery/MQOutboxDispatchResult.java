package io.ddd4j.mq.delivery;

/**
 * 单次 Outbox 调度的汇总结果。
 *
 * @param claimed 已领取数量
 * @param published 已确认发布数量
 * @param rescheduled 已安排重试数量
 * @param dead 预计进入死信数量
 * @param confirmationLost broker 已接收但租约确认丢失数量，后续可能重复投递
 */
public final class MQOutboxDispatchResult {

    private final int claimed;
    private final int published;
    private final int rescheduled;
    private final int dead;
    private final int confirmationLost;

    public MQOutboxDispatchResult(int claimed, int published, int rescheduled, int dead,
                                  int confirmationLost) {
        this.claimed = claimed;
        this.published = published;
        this.rescheduled = rescheduled;
        this.dead = dead;
        this.confirmationLost = confirmationLost;
    }

    public int claimed() { return claimed; }
    public int published() { return published; }
    public int rescheduled() { return rescheduled; }
    public int dead() { return dead; }
    public int confirmationLost() { return confirmationLost; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MQOutboxDispatchResult)) return false;
        MQOutboxDispatchResult that = (MQOutboxDispatchResult) o;
        return claimed == that.claimed && published == that.published
                && rescheduled == that.rescheduled && dead == that.dead
                && confirmationLost == that.confirmationLost;
    }

    @Override
    public int hashCode() {
        int result = claimed;
        result = 31 * result + published;
        result = 31 * result + rescheduled;
        result = 31 * result + dead;
        result = 31 * result + confirmationLost;
        return result;
    }

    @Override
    public String toString() {
        return "MQOutboxDispatchResult{claimed=" + claimed + ", published=" + published
                + ", rescheduled=" + rescheduled + ", dead=" + dead
                + ", confirmationLost=" + confirmationLost + '}';
    }
}
