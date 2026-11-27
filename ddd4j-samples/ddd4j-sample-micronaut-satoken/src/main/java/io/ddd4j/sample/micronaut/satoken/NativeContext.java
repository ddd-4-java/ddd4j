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
    /** \u5f53\u524d\u6846\u67b6\u8bf7\u6c42\u9002\u914d\u5668\u8bfb\u53d6\u7684\u539f\u751f\u8bf7\u6c42\u3002 */
    private final SaRequest request;
    /** \u5bf9\u5e94\u5f53\u524d\u8ba4\u8bc1\u8bf7\u6c42\u7684\u539f\u751f\u54cd\u5e94\u9002\u914d\u5668\u3002 */
    private final SaResponse response;
    /** \u4fdd\u5b58\u672c\u6b21\u8ba4\u8bc1\u8bf7\u6c42\u5c5e\u6027\u7684\u5b58\u50a8\u9002\u914d\u5668\u3002 */
    private final SaStorage storage;

    /**
     * \u4e3a\u5f53\u524d\u8ba4\u8bc1\u8bf7\u6c42\u5efa\u7acb\u53ef\u4f20\u64ad\u5230\u5de5\u4f5c\u7ebf\u7a0b\u7684\u539f\u751f\u4e0a\u4e0b\u6587\u5bf9\u8c61\u3002
     * @param request \u5f53\u524d\u8fdb\u5165 Sa-Token \u7684\u539f\u751f HTTP \u8bf7\u6c42
     * @param response \u5f53\u524d\u539f\u751f HTTP \u54cd\u5e94\u6216\u54cd\u5e94\u6784\u5efa\u5668
     * @param storage \u5f53\u524d\u8bf7\u6c42 Sa-Token \u5c5e\u6027\u5b58\u50a8
     */
    NativeContext(SaRequest request, SaResponse response, SaStorage storage) {
        this.request = request;
        this.response = response;
        this.storage = storage;
    }

    /**
     * \u5728\u6267\u884c\u8ba4\u8bc1\u903b\u8f91\u7684\u5de5\u4f5c\u7ebf\u7a0b\u4e0a\u5b89\u88c5\u5f53\u524d\u8bf7\u6c42\u7684\u539f\u751f\u4e0a\u4e0b\u6587\u3002
     * @return \u9000\u51fa\u5f53\u524d\u8c03\u7528\u65f6\u7528\u4e8e\u6062\u590d\u7684\u72b6\u6001
     */
    @Override
    public SaTokenContextModelBox updateThreadContext() {
        var context = SaManager.getSaTokenContext();
        var previous = context.isValid() ? context.getModelBox() : null;
        context.setContext(request, response, storage);
        return previous;
    }

    /**
     * \u6062\u590d\u8fdb\u5165\u5f53\u524d\u8ba4\u8bc1\u5de5\u4f5c\u7ebf\u7a0b\u4e4b\u524d\u7684\u539f\u751f\u72b6\u6001\u3002
     * @param previous \u8981\u6062\u590d\u7684\u7ebf\u7a0b\u8ba4\u8bc1\u4e0a\u4e0b\u6587
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
