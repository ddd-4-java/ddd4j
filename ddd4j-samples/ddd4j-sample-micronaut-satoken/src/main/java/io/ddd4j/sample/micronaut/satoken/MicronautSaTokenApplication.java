package io.ddd4j.sample.micronaut.satoken;

import io.micronaut.runtime.Micronaut;

/**
 * 原生 Micronaut satoken 样例入口。
 */
public final class MicronautSaTokenApplication {
    /** @param args \u542f\u52a8\u53c2\u6570 */
    public static void main(String[] args) {
        Micronaut.run(MicronautSaTokenApplication.class, args);
    }
}
