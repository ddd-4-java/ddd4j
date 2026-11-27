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
     * 调用 <code>Class.forName()</code>、<code>context.register()</code>、<code>servlet.init()</code> 执行当前夹具操作。
     *
     * @param args 从命令行传入测试探针的参数
     * @throws Exception 反射访问被测成员时失败
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
         * 为当前用例提供固定的 <code>handle</code> 返回值。
         *
         * @return <code>"webmvc-ok"</code>
         */
        @GetMapping("/r05-no-logs")
        public String handle() {
            return "webmvc-ok";
        }
    }
}
