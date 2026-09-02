package io.ddd4j.core.subject;

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
