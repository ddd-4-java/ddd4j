package io.ddd4j.core.subject;

import io.ddd4j.core.util.SubjectKit;


/**
 * Subject 工厂 SPI（三鉴权各自实现）。
 *
 * <p>各鉴权实现（sa-token / shiro / security）提供此接口的实现，
 * 由框架适配层在启动时通过 {@link SubjectKit#register(SubjectProvider)} 注册。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface SubjectProvider {

    default Subject getSubject() {
        return SubjectKit.getSubject();
    }

    /**
     * 按账号体系获取 Subject（1.0.x 对齐 3.0.x 契约：默认回落到全局 Subject）。
     *
     * @param realm 账号体系标识（如 "admin"/"user"）
     * @return 对应 realm 的 Subject
     */
    default Subject getSubject(String realm) {
        return getSubject();
    }

}
