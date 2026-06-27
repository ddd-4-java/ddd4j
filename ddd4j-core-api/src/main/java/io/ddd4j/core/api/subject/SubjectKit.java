package io.ddd4j.core.api.subject;

/**
 * Subject 工具类（纯 Java 实现）
 * <p>
 * 使用策略模式，通过 {@link SubjectProvider} 接口实现框架无关的认证授权。
 * 各框架适配层在启动时注册对应的 SubjectProvider 实现。
 *
 * @author Jensen
 * @公众号 架构师修行录
 */
public final class SubjectKit {

    private static volatile SubjectProvider provider;

    private SubjectKit() {
    }

    /**
     * 注册 SubjectProvider
     *
     * @param provider SubjectProvider 实现
     */
    public static void register(SubjectProvider provider) {
        if (provider != null) {
            SubjectKit.provider = provider;
        }
    }

    /**
     * 获取当前 Subject
     *
     * @return Subject 实例，未注册时返回 null
     */
    public static Subject getSubject() {
        if (provider == null) {
            return null;
        }
        return provider.getSubject();
    }

    /**
     * 获取当前认证主体
     *
     * @return AuthPrincipal 实例，未认证时返回 null
     */
    public static AuthPrincipal getPrincipal() {
        Subject subject = getSubject();
        if (subject == null) {
            return null;
        }
        return subject.getPrincipal();
    }
}
