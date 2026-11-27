package io.ddd4j.spring.cqrs;

import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import io.ddd4j.spring.config.SpringCoreConfig;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * 隔离 JVM 的基础 Spring 启动探针；类路径明确移除 JPA 与事件存储构件。
 */
public final class BasicSpringProcess {
    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>BasicSpringProcess</code> \u5b9e\u4f8b\u3002 */
    private BasicSpringProcess() {
    }

    /**
     * \u8c03\u7528 <code>verifyStartup()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param args \u4ece\u547d\u4ee4\u884c\u4f20\u5165\u6d4b\u8bd5\u63a2\u9488\u7684\u53c2\u6570
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    public static void main(String[] args) throws Exception {
        verifyStartup("io.ddd4j.spring.cqrs");
    }

    /**
     * \u8c03\u7528 <code>Class.forName()</code>\u3001<code>context.getBeansOfType()</code>\u3001<code>context.isActive()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param expectedPackage \u4f20\u7ed9 <code>expectedPackage.equals()</code> \u7684 <code>String</code> \u8f93\u5165
     * @throws Exception \u53cd\u5c04\u8bbf\u95ee\u88ab\u6d4b\u6210\u5458\u65f6\u5931\u8d25
     */
    public static void verifyStartup(String expectedPackage) throws Exception {
        for (String absent : new String[]{"javax.persistence.EntityManager", "jakarta.persistence.EntityManager",
                "org.springframework.data.jpa.repository.JpaRepository",
                "io.ddd4j.data.event.store.jpa.JpaEventStore"}) {
            try {
                Class.forName(absent);
                throw new AssertionError("Optional dependency leaked: " + absent);
            } catch (ClassNotFoundException expected) {
                // 此探针必须运行在真实缺失可选类的 JVM 中。
            }
        }
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(SpringCoreConfig.class)) {
            if (!context.getBeansOfType(ViewManager.class).isEmpty()
                    || !context.getBeansOfType(ViewScheduler.class).isEmpty()) {
                throw new AssertionError("Basic mode activated projection");
            }
            if (!context.isActive()) {
                throw new AssertionError("Basic context failed to start");
            }
            String projectPackage = BaseContext.get(ContextConstants.PROJECT_PACKAGE);
            if (!expectedPackage.equals(projectPackage)) {
                throw new AssertionError("Unexpected project package: " + projectPackage);
            }
        }
        System.out.println("BASIC_SPRING_READY_WITHOUT_JPA_OR_EVENTSTORE");
    }
}
