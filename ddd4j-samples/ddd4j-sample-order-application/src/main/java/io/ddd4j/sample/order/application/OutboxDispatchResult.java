package io.ddd4j.sample.order.application;

/**
 * 单次 Outbox 发布批次的结果。
 *
 * @param attempted 已尝试消息数
 * @param published 已确认消息数
 * @param failed 保留重试的失败消息数
 */
public record OutboxDispatchResult(int attempted, int published, int failed) {
}
