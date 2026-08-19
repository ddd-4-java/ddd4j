package io.ddd4j.tx.saga;

import io.ddd4j.tx.TransactionContext;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Saga 步骤（action + compensator）。
 *
 * <p>每个步骤包含：
 * <ul>
 *   <li><b>action</b>：正向操作（如创建订单、扣减库存）</li>
 *   <li><b>compensator</b>：补偿操作（如取消订单、恢复库存）</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * SagaStep<OrderState> step = SagaStep.<OrderState>builder()
 *     .name("deduct-inventory")
 *     .action(ctx -> inventoryService.deduct(ctx.getState().getSku(), ctx.getState().getQuantity()))
 *     .compensator(ctx -> inventoryService.restore(ctx.getState().getSku(), ctx.getState().getQuantity()))
 *     .build();
 * }</pre>
 *
 * @param <S> Saga 状态类型
 * @author hiwepy
 * @since 4.0.0
 */
public class SagaStep<S> {

    private final String name;
    private final Consumer<SagaContext<S>> action;
    private final Consumer<SagaContext<S>> compensator;
    private final boolean optional;

    private SagaStep(Builder<S> builder) {
        this.name = Objects.requireNonNull(builder.name, "name must not be null");
        this.action = Objects.requireNonNull(builder.action, "action must not be null");
        this.compensator = builder.compensator;
        this.optional = builder.optional;
    }

    /**
     * 创建 Builder。
     */
    public static <S> Builder<S> builder() {
        return new Builder<>();
    }

    public String getName() {
        return name;
    }

    public Consumer<SagaContext<S>> getAction() {
        return action;
    }

    public Consumer<SagaContext<S>> getCompensator() {
        return compensator;
    }

    public boolean hasCompensator() {
        return compensator != null;
    }

    public boolean isOptional() {
        return optional;
    }

    /**
     * SagaStep Builder。
     */
    public static class Builder<S> {
        private String name;
        private Consumer<SagaContext<S>> action;
        private Consumer<SagaContext<S>> compensator;
        private boolean optional = false;

        public Builder<S> name(String name) {
            this.name = name;
            return this;
        }

        public Builder<S> action(Consumer<SagaContext<S>> action) {
            this.action = action;
            return this;
        }

        public Builder<S> compensator(Consumer<SagaContext<S>> compensator) {
            this.compensator = compensator;
            return this;
        }

        public Builder<S> optional(boolean optional) {
            this.optional = optional;
            return this;
        }

        public SagaStep<S> build() {
            return new SagaStep<>(this);
        }
    }
}
