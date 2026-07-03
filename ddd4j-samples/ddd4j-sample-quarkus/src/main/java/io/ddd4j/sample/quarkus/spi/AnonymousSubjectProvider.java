package io.ddd4j.sample.quarkus.spi;

import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * 认证主体提供者：返回空（匿名）Subject 的示例实现。
 *
 * <p>作为 CDI {@link ApplicationScoped} Bean，由 ddd4j-runtime-quarkus 启动期自动注入。
 *
 * <p>真实应用应注入基于 sa-token / shiro / spring-security 的 SubjectProvider。
 * 本示例只演示 SPI 注册流程，不参与鉴权逻辑。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@ApplicationScoped
public class AnonymousSubjectProvider implements SubjectProvider {

    @Override
    public Subject getSubject() {
        // 匿名：未登录
        return null;
    }
}