package io.ddd4j.sample.micronaut.satoken;

import io.micronaut.runtime.Micronaut;

/**
 * 原生 Micronaut satoken 样例入口。
 */
public final class MicronautSaTokenApplication {
    /**
     * @param args 启动参数
     */
    public static void main(String[] args) {
        Micronaut.run(MicronautSaTokenApplication.class, args);
    }
}
