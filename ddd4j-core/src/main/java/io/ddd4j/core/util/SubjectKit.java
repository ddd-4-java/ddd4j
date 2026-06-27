package io.ddd4j.core.util;


import io.ddd4j.core.subject.AuthPrincipal;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;

/**
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class SubjectKit {

    public static volatile SubjectProvider subjectProvider;

    public static Subject getSubject() {
        if (subjectProvider == null) {
            synchronized (SubjectKit.class) {
                if (subjectProvider == null) {
                    // Register via SubjectKit.register() or framework-specific adapter
                    throw new IllegalStateException("SubjectProvider not registered. Call SubjectKit.register() or use framework adapter.");
                }
            }
        }
        return subjectProvider.getSubject();
    }

    public static <T extends AuthPrincipal> T getPrincipal(Class<T> clazz) {
        T principal = getSubject().getPrincipal();
        if (clazz.isAssignableFrom(principal.getClass())) {
            return principal;
        }
        return null;
    }

    public static <T extends AuthPrincipal> T getPrincipalByLoginId(Object loginId, Class<T> clazz) {
        T principal = getSubject().getPrincipalByLoginId(loginId);
        if (clazz.isAssignableFrom(principal.getClass())) {
            return principal;
        }
        return null;
    }

    public static <T extends AuthPrincipal> T getPrincipalByToken(String tokenValue, Class<T> clazz) {
        T principal = getSubject().getPrincipalByToken(tokenValue);
        if (clazz.isAssignableFrom(principal.getClass())) {
            return principal;
        }
        return null;
    }

    public static <T extends AuthPrincipal> T getPrincipal() {
        return getSubject().getPrincipal();
    }

    public static <T extends AuthPrincipal> T getPrincipalByLoginId(Object loginId) {
        return getSubject().getPrincipalByLoginId(loginId);
    }

    public static <T extends AuthPrincipal> T getPrincipalByToken(String tokenValue) {
        return getSubject().getPrincipalByToken(tokenValue);
    }

}
