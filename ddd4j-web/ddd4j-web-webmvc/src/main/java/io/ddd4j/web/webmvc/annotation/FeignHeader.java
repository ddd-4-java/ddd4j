package io.ddd4j.web.webmvc.annotation;

import java.lang.annotation.*;

/**
 * Feign 内部请求
 * 自动填充 内部请求标识符 header={"from":"Y}
 * <p>
 * 优先于读取请web请求头 租户、系统id;如果请求头没有；根据配置开启自动从 TenantContextHolder 填充租户ID
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Target(ElementType.METHOD)
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface FeignHeader {


    /**
     * \u662f\u5426\u81ea\u52a8\u4ece\u4e0a\u4e0b\u6587 TenantContextHolder \u8bfb\u53d6\u7cfb\u7edfid sysem-id\uff1b
     *  \u5982\u679c\u65b9\u6cd5\u5165\u53c2\u5df2\u7ecf\u4f20\u9012\u4e86\uff1b\u8be5\u65b9\u6cd5\u4e5f\u4f1a\u8986\u76d6\u5165\u53c2\u8bf7\u6c42\u5934
     *
     * @return \u662f\u5426\u81ea\u52a8\u586b\u5145\u7cfb\u7edf\u6807\u8bc6
     */
    boolean autoFillSystemId() default true;

    /**
     * \u662f\u5426\u81ea\u52a8\u4ece\u4e0a\u4e0b\u6587 TenantContextHolder \u8bfb\u53d6\u7cfb\u7edftenant-id;
     *  \u5982\u679c\u65b9\u6cd5\u5165\u53c2\u5df2\u7ecf\u4f20\u9012\u4e86\uff1b\u8be5\u65b9\u6cd5\u4e5f\u4f1a\u8986\u76d6\u5165\u53c2\u8bf7\u6c42\u5934
     *
     * @return \u662f\u5426\u81ea\u52a8\u586b\u5145\u79df\u6237\u6807\u8bc6
     */
    boolean autoFillTenantId() default true;


    /**
     * \u662f\u5426\u4f7f\u7528 Http\u8bf7\u6c42\u5934\u7684\u53c2\u6570\uff1b\u8be5\u53c2\u6570\u4f1a\u8986\u76d6 autoFillTenantId  autoFillSysemId
     *  \u5f80feign\u7684\u8bf7\u6c42\u5934\u81ea\u52a8\u586b\u5145\u8be5\u53c2\u6570 {"tenant-id","system-id","third-session","enterprise-id","shop-id","app-id","switch-tenant-id","Authorization",
     *  "client-type", "own-language"};
     *  <p>
     *  \u5f53\u540c\u4e00\u4e2a\u8ba4\u8bc1\u4f53\u7cfb\u7684\u65f6\u5019 \u53ef\u4ee5\u5f00\u542f\uff1b\u6bd4\u5982\uff1a mall-admin \u8c03\u7528 upms \u7684\u65f6\u5019
     *
     * @return \u662f\u5426\u8bfb\u53d6 Web \u8bf7\u6c42\u5934
     */
    boolean useWebRequestHeader() default false;
}
