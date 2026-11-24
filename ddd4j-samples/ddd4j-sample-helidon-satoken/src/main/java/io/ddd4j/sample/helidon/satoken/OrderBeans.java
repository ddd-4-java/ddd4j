package io.ddd4j.sample.helidon.satoken;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.auth.satoken.subject.SaTokenSubjectProvider;
import io.ddd4j.sample.order.application.OrderApplicationService;
import io.ddd4j.sample.order.local.InMemoryOrderAdapters;
/** 当前样例只装配共享订单服务与本应用原生认证。
 */
@ApplicationScoped
public class OrderBeans {
 @Produces @Singleton SubjectProvider subjects(NativeAuth auth) { return new SaTokenSubjectProvider(); }
 @Produces @Singleton OrderApplicationService orders() {
  var adapters=new InMemoryOrderAdapters();return new OrderApplicationService(adapters,adapters,adapters,adapters,adapters);
 }
}
