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

import java.util.Locale;

/**
 * Web 框架采集到的原始请求元数据。
 */
public final class WebRequestData {
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
    public WebRequestData(String requestId, String traceId, String tenantId, String authorization,
                           Locale locale, String forwardedFor, String realIp, String remoteAddress,
                           String method, String path) {
        this.requestId=requestId; this.traceId=traceId; this.tenantId=tenantId;
        this.authorization=authorization; this.locale=locale; this.forwardedFor=forwardedFor;
        this.realIp=realIp; this.remoteAddress=remoteAddress; this.method=method; this.path=path;
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
    @Override public boolean equals(Object o) { return this==o || (o instanceof WebRequestData && java.util.Objects.equals(requestId,((WebRequestData)o).requestId()) && java.util.Objects.equals(traceId,((WebRequestData)o).traceId()) && java.util.Objects.equals(tenantId,((WebRequestData)o).tenantId()) && java.util.Objects.equals(authorization,((WebRequestData)o).authorization()) && java.util.Objects.equals(locale,((WebRequestData)o).locale()) && java.util.Objects.equals(forwardedFor,((WebRequestData)o).forwardedFor()) && java.util.Objects.equals(realIp,((WebRequestData)o).realIp()) && java.util.Objects.equals(remoteAddress,((WebRequestData)o).remoteAddress()) && java.util.Objects.equals(method,((WebRequestData)o).method()) && java.util.Objects.equals(path,((WebRequestData)o).path())); }
    @Override public int hashCode() { return java.util.Objects.hash(requestId,traceId,tenantId,authorization,locale,forwardedFor,realIp,remoteAddress,method,path); }
    @Override public String toString() { return "WebRequestData{path="+path+",method="+method+"}"; }
}
