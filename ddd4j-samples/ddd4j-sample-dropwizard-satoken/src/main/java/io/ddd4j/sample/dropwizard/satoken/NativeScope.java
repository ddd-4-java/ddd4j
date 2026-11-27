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
    /** \u7f13\u5b58\u5f53\u524d\u5de5\u4f5c\u7ebf\u7a0b\u7684 Sa-Token \u4e0a\u4e0b\u6587\u6a21\u578b\u3002 */
    private final ThreadLocal<SaTokenContextModelBox> boxes = SaTokenContextForThreadLocalStaff.modelBoxThreadLocal;
    /** \u8fdb\u5165\u8bf7\u6c42\u4e4b\u524d\u7ebf\u7a0b\u7ed1\u5b9a\u7684Sa-Token \u4e0a\u4e0b\u6587\u6a21\u578b\u3002 */
    private final SaTokenContextModelBox before;
    /** \u5f53\u524d\u8bf7\u6c42\u7ed1\u5b9a\u5230\u5f53\u524d\u7ebf\u7a0b\u7684Sa-Token \u4e0a\u4e0b\u6587\u6a21\u578b\u3002 */
    private final SaTokenContextModelBox owned;

    /**
     * \u5c06\u539f\u751f\u8ba4\u8bc1\u4e0a\u4e0b\u6587\u7ed1\u5b9a\u5230\u5f53\u524d\u8bf7\u6c42\u7ebf\u7a0b\u5e76\u4fdd\u5b58\u8fdb\u5165\u524d\u7684\u7ed1\u5b9a\u3002
     * @param ctx \u5f53\u524d\u6846\u67b6\u8bf7\u6c42\u4e0a\u4e0b\u6587
     * @param response \u4f20\u7ed9\u8fc7\u6ee4\u5668\u94fe\u7684 Servlet \u54cd\u5e94
     */
    NativeScope(HttpServletRequest ctx, HttpServletResponse response) {
        before = boxes.get();
        owned = new SaTokenContextModelBox(new SaHttpContext(ctx), SaHttpContext.response(response), SaHttpContext.storage(ctx));
        boxes.set(owned);
    }

    /** \u7ed3\u675f\u5f53\u524d\u8bf7\u6c42\u7684\u8ba4\u8bc1\u4f5c\u7528\u57df\uff0c\u6309\u8fdb\u5165\u65f6\u4fdd\u5b58\u7684\u72b6\u6001\u6062\u590d\u5de5\u4f5c\u7ebf\u7a0b\u3002 */
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
