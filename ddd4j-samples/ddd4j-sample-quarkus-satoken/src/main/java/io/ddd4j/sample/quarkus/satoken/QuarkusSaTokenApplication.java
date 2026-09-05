package io.ddd4j.sample.quarkus.satoken;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

/**
 * Quarkus + Sa-Token 鉴权示例启动类。
 *
 * <p>Quarkus 使用 CDI 容器，ddd4j-runtime-quarkus 在启动期自动注入核心 SPI 到 BaseContext。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@QuarkusMain
public class QuarkusSaTokenApplication {

    public static void main(String[] args) {
        Quarkus.run(args);
    }

}
