package io.ddd4j.sample.micronaut.satoken;
import java.util.Objects;
import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.model.SaTokenContextModelBox;
import cn.dev33.satoken.context.model.SaRequest;
import cn.dev33.satoken.context.model.SaResponse;
import cn.dev33.satoken.context.model.SaStorage;
/** 原生线程传播：逐线程保存并恢复 Sa-Token 前任状态。 */
final class NativeContext implements io.micronaut.core.propagation.ThreadPropagatedContextElement<SaTokenContextModelBox> {
 private final SaRequest request;private final SaResponse response;private final SaStorage storage;
 NativeContext(SaRequest request,SaResponse response,SaStorage storage) { this.request=request;this.response=response;this.storage=storage; }
 @Override public SaTokenContextModelBox updateThreadContext() {
  var context=SaManager.getSaTokenContext();var previous=context.isValid()?context.getModelBox():null;
  context.setContext(request,response,storage);return previous;
 }
 @Override public void restoreThreadContext(SaTokenContextModelBox previous) {
  var context=SaManager.getSaTokenContext();context.clearContext();
  if(Objects.nonNull(previous)) { context.setContext(previous.getRequest(),previous.getResponse(),previous.getStorage()); }
 }
}
