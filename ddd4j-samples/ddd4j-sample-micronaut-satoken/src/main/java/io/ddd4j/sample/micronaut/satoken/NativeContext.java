package io.ddd4j.sample.micronaut.satoken;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.model.SaRequest;
import cn.dev33.satoken.context.model.SaResponse;
import cn.dev33.satoken.context.model.SaStorage;
import cn.dev33.satoken.context.model.SaTokenContextModelBox;

import java.util.Objects;

/**
 * 原生线程传播：逐线程保存并恢复 Sa-Token 前任状态。
 */
final class NativeContext implements io.micronaut.core.propagation.ThreadPropagatedContextElement<SaTokenContextModelBox> {
    /**
     * 当前框架请求适配器读取的原生请求。
     */
    private final SaRequest request;
    /**
     * 对应当前认证请求的原生响应适配器。
     */
    private final SaResponse response;
    /**
     * 保存本次认证请求属性的存储适配器。
     */
    private final SaStorage storage;

    /**
     * 为当前认证请求建立可传播到工作线程的原生上下文对象。
     *
     * @param request  当前进入 Sa-Token 的原生 HTTP 请求
     * @param response 当前原生 HTTP 响应或响应构建器
     * @param storage  当前请求 Sa-Token 属性存储
     */
    NativeContext(SaRequest request, SaResponse response, SaStorage storage) {
        this.request = request;
        this.response = response;
        this.storage = storage;
    }

    /**
     * 在执行认证逻辑的工作线程上安装当前请求的原生上下文。
     *
     * @return 退出当前调用时用于恢复的状态
     */
    @Override
    public SaTokenContextModelBox updateThreadContext() {
        var context = SaManager.getSaTokenContext();
        var previous = context.isValid() ? context.getModelBox() : null;
        context.setContext(request, response, storage);
        return previous;
    }

    /**
     * 恢复进入当前认证工作线程之前的原生状态。
     *
     * @param previous 要恢复的线程认证上下文
     */
    @Override
    public void restoreThreadContext(SaTokenContextModelBox previous) {
        var context = SaManager.getSaTokenContext();
        context.clearContext();
        if (Objects.nonNull(previous)) {
            context.setContext(previous.getRequest(), previous.getResponse(), previous.getStorage());
        }
    }
}
