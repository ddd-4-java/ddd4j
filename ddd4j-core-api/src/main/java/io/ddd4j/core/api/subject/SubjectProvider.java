package io.ddd4j.core.api.subject;

/**
 * Subject 提供者接口
 * <p>
 * 各框架适配层提供实现：
 * <ul>
 *   <li>Spring: 基于 SpringContext.getBean()</li>
 *   <li>Quarkus: 基于 CDI BeanManager</li>
 *   <li>Javalin/Guice: 基于 Injector.getInstance()</li>
 * </ul>
 *
 * @author Jensen
 * @公众号 架构师修行录
 */
public interface SubjectProvider {

    /**
     * 获取当前 Subject
     *
     * @return Subject 实例
     */
    Subject getSubject();
}
