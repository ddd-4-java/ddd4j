/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.ddd4j.web.core.context;

import java.util.Objects;
import java.util.Locale;

/**
 * Web 框架采集到的原始请求元数据。
 */public final class WebRequestData {

    private final String requestId;
    private final String traceId;
    private final String tenantId;
    private final String authorization;
    private final Locale locale;
    private final String forwardedFor;
    private final String realIp;
    private final String remoteAddress;
    private final String method;
    private final String path;

/**
 * Web 框架采集到的原始请求元数据。
 */

    public WebRequestData(String requestId, String traceId, String tenantId, String authorization,
                          Locale locale, String forwardedFor, String realIp, String remoteAddress,
                          String method, String path) {
        this.requestId = requestId;
        this.traceId = traceId;
        this.tenantId = tenantId;
        this.authorization = authorization;
        this.locale = locale;
        this.forwardedFor = forwardedFor;
        this.realIp = realIp;
        this.remoteAddress = remoteAddress;
        this.method = method;
        this.path = path;
    }

    public String requestId() { return requestId; }
    public String traceId() { return traceId; }
    public String tenantId() { return tenantId; }
    public String authorization() { return authorization; }
    public Locale locale() { return locale; }
    public String forwardedFor() { return forwardedFor; }
    public String realIp() { return realIp; }
    public String remoteAddress() { return remoteAddress; }
    public String method() { return method; }
    public String path() { return path; }

    public String getRequestId() {
        return requestId;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getAuthorization() {
        return authorization;
    }

    public Locale getLocale() {
        return locale;
    }

    public String getForwardedFor() {
        return forwardedFor;
    }

    public String getRealIp() {
        return realIp;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WebRequestData)) {
            return false;
        }
        WebRequestData that = (WebRequestData) o;
        return Objects.equals(requestId, that.requestId)
                && Objects.equals(traceId, that.traceId)
                && Objects.equals(tenantId, that.tenantId)
                && Objects.equals(authorization, that.authorization)
                && Objects.equals(locale, that.locale)
                && Objects.equals(forwardedFor, that.forwardedFor)
                && Objects.equals(realIp, that.realIp)
                && Objects.equals(remoteAddress, that.remoteAddress)
                && Objects.equals(method, that.method)
                && Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(requestId);
        result = 31 * result + Objects.hashCode(traceId);
        result = 31 * result + Objects.hashCode(tenantId);
        result = 31 * result + Objects.hashCode(authorization);
        result = 31 * result + Objects.hashCode(locale);
        result = 31 * result + Objects.hashCode(forwardedFor);
        result = 31 * result + Objects.hashCode(realIp);
        result = 31 * result + Objects.hashCode(remoteAddress);
        result = 31 * result + Objects.hashCode(method);
        result = 31 * result + Objects.hashCode(path);
        return result;
    }

    @Override
    public String toString() {
        return "WebRequestData[requestId=" + requestId + ", traceId=" + traceId + ", tenantId=" + tenantId + ", authorization=" + authorization + ", locale=" + locale + ", forwardedFor=" + forwardedFor + ", realIp=" + realIp + ", remoteAddress=" + remoteAddress + ", method=" + method + ", path=" + path + "]";
    }
}
