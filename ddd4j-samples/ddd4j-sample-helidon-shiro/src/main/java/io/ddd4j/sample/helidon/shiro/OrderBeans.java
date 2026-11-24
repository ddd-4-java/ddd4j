package io.ddd4j.sample.helidon.shiro;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.auth.shiro.subject.ShiroSubjectProvider;
import io.ddd4j.sample.order.application.OrderApplicationService;
import io.ddd4j.sample.order.local.InMemoryOrderAdapters;
/** 当前样例只装配共享订单服务与本应用原生认证。
 */
@ApplicationScoped
public class OrderBeans {
 @Produces @Singleton SubjectProvider subjects(NativeAuth auth) { return new ShiroSubjectProvider(); }
 @Produces @Singleton OrderApplicationService orders() {
  var adapters=new InMemoryOrderAdapters();return new OrderApplicationService(adapters,adapters,adapters,adapters,adapters);
 }
}
