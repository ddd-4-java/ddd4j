package io.ddd4j.web.webmvc.logs;

import io.ddd4j.web.webmvc.util.WebUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletConfig;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * 没有日志依赖的真实 Spring MVC 初始化与请求分派入口。
 */
public class WebMvcWithoutLogsProbe {
    /**
     * \u8c03\u7528 <code>Class.forName()</code>\u3001<code>context.register()</code>\u3001<code>servlet.init()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param args \u4ece\u547d\u4ee4\u884c\u4f20\u5165\u6d4b\u8bd5\u63a2\u9488\u7684\u53c2\u6570
     * @throws Exception \u53cd\u5c04\u8bbf\u95ee\u88ab\u6d4b\u6210\u5458\u65f6\u5931\u8d25
     */
    public static void main(String[] args) throws Exception {
        try {
            Class.forName("io.ddd4j.data.logs.ApiOperationLogProvider");
            throw new IllegalStateException("logs unexpectedly present");
        } catch (ClassNotFoundException expected) {
            // 未选择日志时不解析 ServiceLoader 桥接类型。
        }
        try (AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext()) {
            context.register(MvcConfiguration.class, Endpoint.class);
            DispatcherServlet servlet = new DispatcherServlet(context);
            servlet.init(new MockServletConfig());
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/r05-no-logs");
            MockHttpServletResponse response = new MockHttpServletResponse();
            servlet.service(request, response);
            if (response.getStatus() != 200 || !"webmvc-ok".equals(response.getContentAsString())) {
                throw new IllegalStateException("MVC response=" + response.getStatus());
            }
            WebUtils.getRemoteAddr(request);
            servlet.destroy();
            System.out.println("R05_WEBMVC_WITHOUT_LOGS_OK initialized,dispatched,status=200,body=webmvc-ok");
        }
    }

    /**
     * 标准 MVC 配置，包含可选桥接所在包的组件扫描。
     */
    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @org.springframework.context.annotation.ComponentScan(basePackages = "io.ddd4j.web.webmvc.logs")
    static class MvcConfiguration {
    }

    /**
     * 实际请求分派目标。
     */
    @RestController
    public static class Endpoint {
        /**
         * \u4e3a\u5f53\u524d\u7528\u4f8b\u63d0\u4f9b\u56fa\u5b9a\u7684 <code>handle</code> \u8fd4\u56de\u503c\u3002
         * @return <code>"webmvc-ok"</code>
         */
        @GetMapping("/r05-no-logs")
        public String handle() {
            return "webmvc-ok";
        }
    }
}
