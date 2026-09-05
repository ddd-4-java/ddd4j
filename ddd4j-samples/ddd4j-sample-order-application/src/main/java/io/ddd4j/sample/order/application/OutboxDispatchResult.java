package io.ddd4j.sample.order.application;

import java.util.Objects;
/**
 * 单次 Outbox 发布批次的结果。
 *
 * @param attempted 已尝试消息数
 * @param published 已确认消息数
 * @param failed 保留重试的失败消息数
 */public final class OutboxDispatchResult {
        private final int attempted;
        private final int published;
        private final int failed;

        public OutboxDispatchResult(int attempted, int published, int failed) {
            this.attempted = attempted;
            this.published = published;
            this.failed = failed;
        }
        public int attempted() { return attempted; }
        public int published() { return published; }
        public int failed() { return failed; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
        OutboxDispatchResult other = (OutboxDispatchResult) o;
            return Objects.equals(this.attempted, other.attempted) && Objects.equals(this.published, other.published) && Objects.equals(this.failed, other.failed);
        }
        @Override
        public int hashCode() { return java.util.Objects.hash(attempted, published, failed); }
        @Override
        public String toString() {
            return "OutboxDispatchResult{" + "attempted=" + attempted + ", " + "published=" + published + ", " + "failed=" + failed + "}";
        }
    }
