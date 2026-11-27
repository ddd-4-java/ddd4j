package io.ddd4j.sample.micronaut.cqrs;

import io.micronaut.runtime.Micronaut;

/**
 * 只装配共享四表商品交易和 Micronaut 原生命令总线。
 */
public final class MicronautCqrsApplication {
    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>MicronautCqrsApplication</code> \u5b9e\u4f8b\u3002 */
    private MicronautCqrsApplication() {
    }

    /**
     * \u542f\u52a8 Micronaut \u5546\u54c1\u4ea4\u6613 CQRS \u793a\u4f8b\u3002
     * @param args \u5e94\u7528\u542f\u52a8\u53c2\u6570
     */
    public static void main(String[] args) {
        Micronaut.run(MicronautCqrsApplication.class, args);
    }
}
