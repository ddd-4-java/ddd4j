/*
 * Copyright 2017-2026 the original author hiwepy.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.guice.web;

import io.ddd4j.guice.util.WebKit;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * Javalin MDC 处理器（等价于 Spring 的 Slf4jMDCInterceptor）。
 * <p>
 * 在请求处理前设置 MDC（requestId, requestURL, remoteAddr, method），
 * 请求处理后清理 MDC。
 * <p>
 * 使用方式：
 * <pre>{@code
 * JavalinMDCPlugin mdcPlugin = new JavalinMDCPlugin();
 * app.before(mdcPlugin);
 * app.after(ctx -> MDC.clear());
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class JavalinMDCPlugin implements Handler {

    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_REQUEST_URL = "requestURL";
    public static final String MDC_REMOTE_ADDR = "remoteAddr";
    public static final String MDC_METHOD = "method";

    /**
     * 创建 MDC 清理 Handler（用于 app.after()）
     */
    public static Handler afterHandler() {
        return ctx -> MDC.clear();
    }

    @Override
    public void handle(Context ctx) {
        String requestId = ctx.header("X-Request-Id");
        if (java.util.Objects.isNull(requestId) || io.ddd4j.kit.lang.StrKit.isEmpty(requestId)) {
            requestId = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put(MDC_REQUEST_ID, requestId);
        MDC.put(MDC_REQUEST_URL, ctx.url());
        MDC.put(MDC_REMOTE_ADDR, WebKit.getClientIp(ctx));
        MDC.put(MDC_METHOD, ctx.method().name());
        // 设置响应头
        ctx.header("X-Request-Id", requestId);
    }
}
