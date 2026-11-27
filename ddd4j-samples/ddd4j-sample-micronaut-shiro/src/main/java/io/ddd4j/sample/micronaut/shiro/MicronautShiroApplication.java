package io.ddd4j.sample.micronaut.shiro;

import io.micronaut.runtime.Micronaut;

/**
 * 原生 Micronaut shiro 样例入口。
 */
public final class MicronautShiroApplication {
    /**
     * @param args 启动参数
     */
    public static void main(String[] args) {
        Micronaut.run(MicronautShiroApplication.class, args);
    }
}
