package io.ddd4j.sample.dropwizard.satoken;

import cn.dev33.satoken.context.SaTokenContextForThreadLocalStaff;
import cn.dev33.satoken.context.model.SaTokenContextModelBox;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Objects;

/**
 * 原生 Sa-Token 工作线程作用域；恢复调用前请求。
 */
final class NativeScope implements AutoCloseable {
    /**
     * 缓存当前工作线程的 Sa-Token 上下文模型。
     */
    private final ThreadLocal<SaTokenContextModelBox> boxes = SaTokenContextForThreadLocalStaff.modelBoxThreadLocal;
    /**
     * 进入请求之前线程绑定的Sa-Token 上下文模型。
     */
    private final SaTokenContextModelBox before;
    /**
     * 当前请求绑定到当前线程的Sa-Token 上下文模型。
     */
    private final SaTokenContextModelBox owned;

    /**
     * 将原生认证上下文绑定到当前请求线程并保存进入前的绑定。
     *
     * @param ctx      当前框架请求上下文
     * @param response 传给过滤器链的 Servlet 响应
     */
    NativeScope(HttpServletRequest ctx, HttpServletResponse response) {
        before = boxes.get();
        owned = new SaTokenContextModelBox(new SaHttpContext(ctx), SaHttpContext.response(response), SaHttpContext.storage(ctx));
        boxes.set(owned);
    }

    /**
     * 结束当前请求的认证作用域，按进入时保存的状态恢复工作线程。
     */
    @Override
    public void close() {
        if (boxes.get() == owned) {
            if (Objects.isNull(before)) {
                boxes.remove();
            } else {
                boxes.set(before);
            }
        }
    }
}
