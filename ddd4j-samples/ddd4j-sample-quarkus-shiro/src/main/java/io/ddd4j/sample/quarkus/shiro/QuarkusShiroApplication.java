package io.ddd4j.sample.quarkus.shiro;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

/**
 * Quarkus + Apache Shiro 鉴权示例启动类。
 *
 * <p>演示 Quarkus CDI 容器下 Shiro 底层的 SubjectKit 统一鉴权入口，
 * 与 Quarkus + Sa-Token 示例业务代码完全一致。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@QuarkusMain
public class QuarkusShiroApplication {

    public static void main(String[] args) {
        Quarkus.run(args);
    }

}
