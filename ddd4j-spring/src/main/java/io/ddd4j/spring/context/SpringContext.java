package io.ddd4j.spring.context;

import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.contract.constant.ContextConstants;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Primary;
import org.springframework.core.PriorityOrdered;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Spring上下文：显式获取SpringBean、注册Bean；SpringEvent事件发布
 *
 * @author Loong Wan
 */
@Slf4j(topic = "### BASE-CORE : SpringContext ###")
@Primary
@Order(PriorityOrdered.HIGHEST_PRECEDENCE)
public class SpringContext implements ApplicationContextAware {
    // 应用启动完成的信号
    public static final CountDownLatch APP_START_SIGNAL = new CountDownLatch(1);
    // Spring上下文初始化完成的信号
    public static final CountDownLatch APPLICATION_CONTEXT_START_SIGNAL = new CountDownLatch(1);
    private static ApplicationContext APPLICATION_CONTEXT;
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newSingleThreadExecutor();

    public SpringContext() {
        log.debug("Loading SpringContext");
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stackTraceElements) {
            if (element.getMethodName().equals("main")) {
                try {
                    String projectPackage = Class.forName(element.getClassName()).getPackage().getName();
                    BaseContext.inject(ContextConstants.PROJECT_PACKAGE, projectPackage);
                    log.debug("PROJECT_PACKAGE: {}", projectPackage);
                } catch (ClassNotFoundException e) {
                    log.error("Cannot find class: {}", element.getClassName());
                }
                break;
            }
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        SpringContext.APPLICATION_CONTEXT = applicationContext;
        APPLICATION_CONTEXT_START_SIGNAL.countDown();
    }

    /**
     * 应用上下文刷新完成后释放启动等待信号。
     */
    @EventListener
    public void onContextRefreshed(ContextRefreshedEvent event) {
        APP_START_SIGNAL.countDown();
    }


    public static void onAppStarted(Consumer<ApplicationContext> then) {
        EXECUTOR_SERVICE.submit(() -> {
            try {
                APP_START_SIGNAL.await();
                then.accept(APPLICATION_CONTEXT);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("应用初始化时线程被中断", e);
            } catch (Exception e) {
                log.error("执行应用启动后置逻辑失败", e);
            }
        });
    }

    // 获取ApplicationContext，需等待ApplicationContext初始化完成
    @SneakyThrows
    public static ApplicationContext getApplicationContext() {
        // 阻塞等待初始化完成
        if (APPLICATION_CONTEXT == null) {
            APPLICATION_CONTEXT_START_SIGNAL.await();
        }
        return APPLICATION_CONTEXT;
    }

    public static <T> T getBean(@NonNull String name) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("获取的Bean名称不能为空");
        }
        return (T) getApplicationContext().getBean(name);
    }

    public static <T> T getBean(@NonNull String name, @NonNull Class<T> clazz) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("获取的Bean名称不能为空");
        }
        return getApplicationContext().getBean(name, clazz);
    }

    public static <T> T getBean(@NonNull Class<T> clazz) {
        return getApplicationContext().getBean(clazz);
    }

    // 获取Bean，需等待应用完全启动
    @SneakyThrows
    public static <T> T getBeanAwait(@NonNull Class<T> clazz) {
        APP_START_SIGNAL.await();
        return getBean(clazz);
    }

    // 获取Bean，需等待应用完全启动
    public static <T> void getBean(@NonNull Class<T> beanClazz, Consumer<T> then) {
        then.accept(getBeanAwait(beanClazz));
    }

    public static <T> Collection<T> getBeans(@NonNull Class<T> clazz) {
        Map<String, T> beansOfType = getApplicationContext().getBeansOfType(clazz);
        if (CollectionUtils.isEmpty(beansOfType)) {
            return new ArrayList<>();
        }
        return beansOfType.values();
    }

    public static Environment getEnv() {
        return getApplicationContext().getEnvironment();
    }

}