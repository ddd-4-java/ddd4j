package io.ddd4j.annotation.api;

import io.ddd4j.annotation.Contract;

import java.lang.annotation.*;

/**
 * API 幂等性注解。
 *
 * <p>用于标注 Controller 方法或类，启用接口幂等性保护。
 * 支持基于请求参数（ARGS）或 Token 的幂等控制策略，可配置过期时间、重试次数等参数。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Contract
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface ApiIdempotent {

    /**
     * \u5e42\u7b49Key\uff1a\u9ed8\u8ba4\u4e3a\u7a7a\uff1b
     *   type\u4e3aArgs\u65f6\u81ea\u52a8\u83b7\u53d6 @RequestMapping\u3001@PostMapping\u3001@GetMapping\u3001@PutMapping\u3001@DeleteMapping\u3001@PatchMapping \u7684 value \u503c\uff1b
     *   type\u4e3aToken\u65f6\u8be5\u503c\u7528\u4e8e\u544a\u8bc9\u62e6\u622a\u5668\u53d6\u503c\u7684\u53c2\u6570\u540d
     * @return \u5e42\u7b49\u952e\u6216 Token \u53c2\u6570\u540d
     */
    String value() default "";

    /**
     * \u5e42\u7b49\u65b9\u5f0f
     *
     * @return \u5e42\u7b49\u65b9\u5f0f
     */
    ApiIdempotentType type() default ApiIdempotentType.ARGS;

    /**
     * \u662f\u5426\u542f\u7528 Spring Expression Language(SpEL) \u8868\u8fbe\u5f0f\u89e3\u6790value\u503c
     *
     * @return \u662f\u5426\u89e3\u6790 SpEL \u8868\u8fbe\u5f0f
     */
    boolean spel() default false;

    /**
     * \u662f\u5426\u5c06\u53c2\u6570\u4f5c\u4e3a\u5e42\u7b49key\u7684\u4e00\u90e8\u5206
     *
     * @return \u662f\u5426\u5305\u542b\u8bf7\u6c42\u53c2\u6570
     */
    boolean withArgs() default false;

    /**
     * \u5e42\u7b49\u8fc7\u671f\u65f6\u95f4\uff0c\u9ed8\u8ba4 2000 \u6beb\u79d2\uff0c\u5373\uff1a\u5728\u6b64\u65f6\u95f4\u6bb5\u5185\uff0c\u5bf9API\u8fdb\u884c\u5e42\u7b49\u5904\u7406\u3002
     *
     * @return \u8fc7\u671f\u65f6\u95f4\uff0c\u5355\u4f4d\u6beb\u79d2
     */
    long expireMillis() default 2000;

    /**
     * \u91cd\u8bd5\u6b21\u6570\uff0c\u9ed8\u8ba40
     *
     * @return \u91cd\u8bd5\u6b21\u6570
     */
    int retryTimes() default 0;

    /**
     * \u91cd\u8bd5\u95f4\u9694\u65f6\u95f4\uff0c\u5355\u4f4d\uff1ams\uff0c\u9ed8\u8ba4100
     *
     * @return \u91cd\u8bd5\u95f4\u9694\uff0c\u5355\u4f4d\u6beb\u79d2
     */
    long retryInterval() default 100;

    /**
     * \u662f\u5426\u81ea\u52a8\u8fdb\u884c\u89e3\u9501\u64cd\u4f5c\uff0c\u9ed8\u8ba4\uff1afalse, \u7b49\u5f85key\u8fc7\u671f
     *
     * @return \u662f\u5426\u81ea\u52a8\u89e3\u9501
     */
    boolean unlock() default false;

}
