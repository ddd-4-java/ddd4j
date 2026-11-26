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
    private final ThreadLocal<SaTokenContextModelBox> boxes = SaTokenContextForThreadLocalStaff.modelBoxThreadLocal;
    private final SaTokenContextModelBox before;
    private final SaTokenContextModelBox owned;

    NativeScope(HttpServletRequest ctx, HttpServletResponse response) {
        before = boxes.get();
        owned = new SaTokenContextModelBox(new SaHttpContext(ctx), SaHttpContext.response(response), SaHttpContext.storage(ctx));
        boxes.set(owned);
    }

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
