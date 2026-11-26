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
    private BasicSpringProcess() {
    }

    public static void main(String[] args) throws Exception {
        verifyStartup("io.ddd4j.spring.cqrs");
    }

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
