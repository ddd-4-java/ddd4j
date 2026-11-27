package io.ddd4j.core.context;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 具备失败回滚和关闭清理能力的全局 SPI 注册作用域。
 */
public final class SpiRegistrationScope implements AutoCloseable {

    /** \u7b49\u5f85\u6309\u987a\u5e8f\u5b89\u88c5\u7684\u670d\u52a1\u6ce8\u518c\u3002 */
    private final List<Registration<?>> registrations = new ArrayList<>();
    /** \u5df2\u5b89\u88c5\u3001\u5173\u95ed\u65f6\u6309\u53cd\u5e8f\u6062\u590d\u7684\u670d\u52a1\u6ce8\u518c\u3002 */
    private final List<Registration<?>> installed = new ArrayList<>();
    /** \u5f53\u524d\u6ce8\u518c\u4f5c\u7528\u57df\u662f\u5426\u5df2\u6267\u884c\u5b89\u88c5\u3002 */
    private boolean started;

    /**
     * \u767b\u8bb0\u4f5c\u7528\u57df\u542f\u52a8\u65f6\u8981\u5b89\u88c5\u7684\u670d\u52a1\u3002
     * @param <T> \u670d\u52a1\u7c7b\u578b
     * @param key \u670d\u52a1\u6807\u8bc6
     * @param type \u670d\u52a1\u63a5\u53e3\u7c7b\u578b
     * @param service \u670d\u52a1\u5b9e\u4f8b
     * @return \u5f53\u524d\u767b\u8bb0\u4f5c\u7528\u57df
     */
    public synchronized <T> SpiRegistrationScope register(String key, Class<T> type, T service) {
        if (started) {
            throw new IllegalStateException("SPI scope has already started");
        }
        registrations.add(new Registration<>(key, type, service));
        return this;
    }

    /** \u5b89\u88c5\u5df2\u767b\u8bb0\u7684\u670d\u52a1\uff1b\u91cd\u590d\u8c03\u7528\u4e0d\u518d\u5b89\u88c5\u3002 */
    public synchronized void start() {
        if (started) {
            return;
        }
        try {
            for (Registration<?> registration : registrations) {
                registration.install();
                installed.add(registration);
            }
            started = true;
        } catch (RuntimeException exception) {
            rollback();
            throw exception;
        }
    }

    /**
     * 结束当前组件持有的资源。
     *
     * <p>此实现调用 <code>rollback()</code>。</p>
     */
    @Override
    public synchronized void close() {
        rollback();
        started = false;
        ThreadContext.clear();
    }

    /** \u6309\u5b89\u88c5\u7684\u9006\u5e8f\u6ce8\u9500\u5df2\u88c5\u914d\u670d\u52a1\uff0c\u6062\u590d\u4ecd\u7531\u672c\u4f5c\u7528\u57df\u6301\u6709\u7684\u539f\u6ce8\u518c\u3002 */
    private void rollback() {
        for (int index = installed.size() - 1; index >= 0; index--) {
            installed.get(index).uninstall();
        }
        installed.clear();
    }

    /**
     * \u4fdd\u5b58 SPI \u6ce8\u518c\u7684\u952e\u3001\u670d\u52a1\u7c7b\u578b\u3001\u5b9e\u4f8b\u53ca\u9000\u51fa\u65f6\u9700\u8981\u6062\u590d\u7684\u539f\u670d\u52a1\u3002
     * @param <T> \u6ce8\u518c\u7684 SPI \u670d\u52a1\u7c7b\u578b
     */
    private static final class Registration<T> {

        /** \u53c2\u4e0e SPI \u6ce8\u518c\u53ca\u67e5\u627e\u7684\u952e\u3002 */
        private final String key;
        /** \u5f53\u524d\u767b\u8bb0\u7684 SPI \u670d\u52a1\u7c7b\u578b\u3002 */
        private final Class<T> type;
        /** \u5f53\u524d\u6ce8\u518c\u4f5c\u7528\u57df\u63d0\u4f9b\u7684\u670d\u52a1\u5b9e\u4f8b\u3002 */
        private final T service;
        /** \u672c\u4f5c\u7528\u57df\u767b\u8bb0\u524d\u540c\u4e00\u4e2a\u952e\u4e0a\u7684\u670d\u52a1\u5b9e\u4f8b\u3002 */
        private Optional<T> previous = Optional.empty();

        /**
         * \u4fdd\u5b58\u5f85\u5b89\u88c5\u7684 SPI \u670d\u52a1\u53ca\u5176\u7c7b\u578b\u4fe1\u606f\u3002
         * @param key \u670d\u52a1\u6ce8\u518c\u952e
         * @param type \u53ef\u7528\u4e8e\u7c7b\u578b\u68c0\u67e5\u7684 SPI \u670d\u52a1\u7c7b\u578b
         * @param service \u672c\u4f5c\u7528\u57df\u8981\u6ce8\u518c\u7684\u670d\u52a1\u5b9e\u4f8b
         */
        private Registration(String key, Class<T> type, T service) {
            this.key = Objects.requireNonNull(key, "key must not be null");
            this.type = Objects.requireNonNull(type, "type must not be null");
            this.service = Objects.requireNonNull(service, "service must not be null");
        }

        /** \u4fdd\u5b58\u5171\u4eab\u952e\u4e0a\u7684\u65e7\u503c\u5e76\u767b\u8bb0\u5f53\u524d\u4f5c\u7528\u57df\u63d0\u4f9b\u7684\u670d\u52a1\u5b9e\u4f8b\u3002 */
        private void install() {
            previous = BaseContext.get(key, type);
            BaseContext.inject(key, type, service);
        }

        /** \u5f53\u524d\u670d\u52a1\u4ecd\u62e5\u6709\u5171\u4eab\u952e\u65f6\u6062\u590d\u65e7\u503c\uff0c\u539f\u6765\u65e0\u503c\u65f6\u79fb\u9664\u952e\u3002 */
        private void uninstall() {
            BaseContext.get(key, type)
                    .filter(current -> current == service)
                    .ifPresent(ignored -> {
                        if (previous.isPresent()) {
                            BaseContext.inject(key, type, previous.orElseThrow());
                        } else {
                            BaseContext.remove(key);
                        }
                    });
            previous = Optional.empty();
        }
    }
}
