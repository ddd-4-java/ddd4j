package io.ddd4j.web.webflux.util;

import io.ddd4j.core.constant.XHeaders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * WebFlux \u8bf7\u6c42\u5904\u7406\u5de5\u5177\uff1aAJAX \u5224\u5b9a\u3001\u8bf7\u6c42\u4f53\u8bfb\u53d6\u3001\u5ba2\u6237\u7aef IP \u89e3\u6790\u4e0e\u8bbe\u5907\u6807\u8bc6\u63d0\u53d6\u3002
 * @since 2.0.x
 */
@Slf4j
public class WebFluxKit {

    /** XMLHttpRequest \u4f7f\u7528\u7684\u8bf7\u6c42\u5934\u503c\u3002 */
    private static final String XML_HTTP_REQUEST = "XMLHttpRequest";
    /** \u6807\u8bc6 AJAX \u8bf7\u6c42\u6765\u6e90\u7684 HTTP \u5934\u540d\u79f0\u3002 */
    private static final String X_REQUESTED_WITH = "X-Requested-With";
    /** \u6807\u8bc6 JSON \u5185\u5bb9\u7684\u5a92\u4f53\u7c7b\u578b\u3002 */
    private static final String CONTENT_TYPE_JSON = "application/json";
    /** WebFlux \u5de5\u5177\u5904\u7406\u7684\u81ea\u5b9a\u4e49\u8bf7\u6c42\u5934\u96c6\u5408\u3002 */
    private static final String[] xheaders = new String[]{"X-Forwarded-For", "x-forwarded-for"};
    /** WebFlux \u5de5\u5177\u5904\u7406\u7684\u6807\u51c6 HTTP \u8bf7\u6c42\u5934\u96c6\u5408\u3002 */
    private static final String[] headers = new String[]{"Cdn-Src-Ip", "Proxy-Client-IP", "WL-Proxy-Client-IP", "X-Real-IP", "HTTP_CLIENT_IP", "HTTP_X_FORWARDED_FOR"};
    /** IPv4 \u56de\u73af\u5730\u5740\u3002 */
    private static final String LOCAL_HOST = "localhost";
    /** IPv6 \u56de\u73af\u5730\u5740\u3002 */
    private static final String LOCAL_IP6 = "0:0:0:0:0:0:0:1";
    /** \u672c\u5730\u5ba2\u6237\u7aef\u8fde\u63a5\u7684\u56de\u73af\u5730\u5740\u3002 */
    private static final String LOCAL_IP = "127.0.0.1";
    /** \u4ee3\u7406\u5730\u5740\u672a\u77e5\u65f6\u8fd4\u56de\u7684\u6807\u8bc6\u3002 */
    private static final String UNKNOWN = "unknown";

    /**
     * \u5224\u65ad\u662f\u5426\u4e3a AJAX \u98ce\u683c\u8bf7\u6c42\uff08AJAX \u5934\u3001JSON \u5185\u5bb9\u7c7b\u578b\u6216 POST \u4efb\u4e00\u547d\u4e2d\uff09\u3002
     * @param request \u670d\u52a1\u7aef\u8bf7\u6c42
     * @return {@code true} \u8868\u793a AJAX \u98ce\u683c\u8bf7\u6c42
     */
    public static boolean isAjaxResponse(ServerHttpRequest request) {
        return isAjaxRequest(request) || isContentTypeJson(request) || isPostRequest(request);
    }

    /**
     * \u5224\u65ad\u662f\u5426\u4e3a JSON \u5bf9\u8c61\u63d0\u4ea4\u8bf7\u6c42\uff08POST + JSON \u5185\u5bb9\u7c7b\u578b\uff09\u3002
     * @param request \u670d\u52a1\u7aef\u8bf7\u6c42
     * @return {@code true} \u8868\u793a\u5bf9\u8c61\u63d0\u4ea4\u8bf7\u6c42
     */
    public static boolean isObjectRequest(ServerHttpRequest request) {
        return isPostRequest(request) && isContentTypeJson(request);
    }

    /**
     * \u5224\u65ad\u662f\u5426\u4e3a JSON \u5bf9\u8c61\u63d0\u4ea4\u8bf7\u6c42\uff08POST + JSON \u5185\u5bb9\u7c7b\u578b\uff09\u3002
     * @param request \u901a\u7528 HTTP \u8bf7\u6c42
     * @return {@code true} \u8868\u793a\u5bf9\u8c61\u63d0\u4ea4\u8bf7\u6c42
     */
    public static boolean isObjectRequest(HttpRequest request) {
        return isPostRequest(request) && isContentTypeJson(request);
    }

    /**
     * \u4f9d\u636e {@code X-Requested-With} \u5934\u5224\u65ad\u662f\u5426\u4e3a AJAX \u8bf7\u6c42\u3002
     * @param request \u670d\u52a1\u7aef\u8bf7\u6c42
     * @return {@code true} \u8868\u793a AJAX \u8bf7\u6c42
     */
    public static boolean isAjaxRequest(ServerHttpRequest request) {
        return XML_HTTP_REQUEST.equals(request.getHeaders().getFirst(X_REQUESTED_WITH));
    }

    /**
     * \u4f9d\u636e {@code X-Requested-With} \u5934\u5224\u65ad\u662f\u5426\u4e3a AJAX \u8bf7\u6c42\u3002
     * @param request \u901a\u7528 HTTP \u8bf7\u6c42
     * @return {@code true} \u8868\u793a AJAX \u8bf7\u6c42
     */
    public static boolean isAjaxRequest(HttpRequest request) {
        return Objects.requireNonNull(request.getHeaders().get(X_REQUESTED_WITH)).contains(XML_HTTP_REQUEST);
    }

    /**
     * \u5224\u65ad\u5185\u5bb9\u7c7b\u578b\u662f\u5426\u5305\u542b JSON\u3002
     * @param request \u670d\u52a1\u7aef\u8bf7\u6c42
     * @return {@code true} \u8868\u793a\u5185\u5bb9\u7c7b\u578b\u4e3a JSON
     */
    public static boolean isContentTypeJson(ServerHttpRequest request) {
        return Objects.requireNonNull(request.getHeaders().get(HttpHeaders.CONTENT_TYPE)).contains(CONTENT_TYPE_JSON);
    }

    /**
     * \u5224\u65ad\u5185\u5bb9\u7c7b\u578b\u662f\u5426\u5305\u542b JSON\u3002
     * @param request \u901a\u7528 HTTP \u8bf7\u6c42
     * @return {@code true} \u8868\u793a\u5185\u5bb9\u7c7b\u578b\u4e3a JSON
     */
    public static boolean isContentTypeJson(HttpRequest request) {
        return Objects.requireNonNull(request.getHeaders().get(HttpHeaders.CONTENT_TYPE)).contains(CONTENT_TYPE_JSON);
    }

    /**
     * \u5224\u65ad\u662f\u5426\u4e3a POST \u8bf7\u6c42\u3002
     * @param request \u670d\u52a1\u7aef\u8bf7\u6c42
     * @return {@code true} \u8868\u793a POST \u8bf7\u6c42
     */
    public static boolean isPostRequest(ServerHttpRequest request) {
        return HttpMethod.POST.compareTo(request.getMethod()) == 0;
    }

    /**
     * \u5224\u65ad\u662f\u5426\u4e3a POST \u8bf7\u6c42\u3002
     * @param request \u901a\u7528 HTTP \u8bf7\u6c42
     * @return {@code true} \u8868\u793a POST \u8bf7\u6c42
     */
    public static boolean isPostRequest(HttpRequest request) {
        return HttpMethod.POST.compareTo(request.getMethod()) == 0;
    }

    /**
     * \u4ece Flux<DataBuffer> \u4e2d\u83b7\u53d6\u8bf7\u6c42\u4f53\u5b57\u7b26\u4e32\u3002
     *
     * @param serverHttpRequest \u670d\u52a1\u7aef\u8bf7\u6c42
     * @return \u8bf7\u6c42\u4f53 UTF-8 \u5b57\u7b26\u4e32\uff0c\u7a7a\u8bf7\u6c42\u4f53\u65f6\u4e3a\u7a7a\u4e32
     */
    public static Mono<String> resolveBodyFromRequest(ServerHttpRequest serverHttpRequest) {
        if (serverHttpRequest.getHeaders().getContentLength() == 0) {
            return Mono.just(org.apache.commons.lang3.StringUtils.EMPTY);
        }
        return DataBufferUtils.join(serverHttpRequest.getBody())
                .map(buffer -> {
                    try {
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes);
                        return new String(bytes, StandardCharsets.UTF_8);
                    } finally {
                        DataBufferUtils.release(buffer);
                    }
                })
                .defaultIfEmpty(org.apache.commons.lang3.StringUtils.EMPTY);
    }

    /**
     * \u83b7\u53d6\u8bf7\u6c42\u5ba2\u6237\u7aef IP \u5730\u5740\uff0c\u652f\u6301\u4ee3\u7406\u670d\u52a1\u5668\u3002
     *
     * @param request \u670d\u52a1\u7aef\u8bf7\u6c42
     * @return \u5ba2\u6237\u7aef IP \u5730\u5740\uff08\u4ee3\u7406\u5934\u4f18\u5148\uff0c\u56de\u73af\u5730\u5740\u5f52\u4e00\u4e3a {@code 127.0.0.1}\uff09
     */
    public static String getRemoteAddr(ServerHttpRequest request) {
        String remoteAddr = UNKNOWN;
        for (String xheader : xheaders) {
            remoteAddr = request.getHeaders().getFirst(xheader);
            log.debug(" {} : {} ", xheader, remoteAddr);
            if (StringUtils.hasText(remoteAddr) && !UNKNOWN.equalsIgnoreCase(remoteAddr)) {
                if (remoteAddr.contains(",")) {
                    remoteAddr = remoteAddr.split(",")[0];
                }
                break;
            }
        }
        if (!StringUtils.hasText(remoteAddr) || UNKNOWN.equalsIgnoreCase(remoteAddr)) {
            for (String header : headers) {
                remoteAddr = request.getHeaders().getFirst(header);
                log.debug(" {} : {} ", header, remoteAddr);
                if (StringUtils.hasText(remoteAddr) && !UNKNOWN.equalsIgnoreCase(remoteAddr)) {
                    break;
                }
            }
        }

        if (!StringUtils.hasText(remoteAddr) || UNKNOWN.equalsIgnoreCase(remoteAddr)) {
            remoteAddr = Objects.requireNonNull(request.getRemoteAddress()).getAddress().getHostAddress();
        }
        if (LOCAL_HOST.equals(remoteAddr) || LOCAL_IP6.equals(remoteAddr)) {
            remoteAddr = LOCAL_IP;
        }

        return remoteAddr;
    }

    /**
     * \u5224\u65ad\u8fdc\u7aef\u5ba2\u6237\u7aef\u4e0e\u672c\u673a\u662f\u5426\u5904\u4e8e\u540c\u4e00 /24 \u7f51\u6bb5\u3002
     * @param request \u670d\u52a1\u7aef\u8bf7\u6c42
     * @return {@code true} \u8868\u793a\u540c\u4e00\u7f51\u6bb5
     */
    public static boolean isSameSegment(ServerHttpRequest request) {
        String localIp = Objects.requireNonNull(request.getLocalAddress()).getAddress().getHostAddress();
        String remoteIp = getRemoteAddr(request);
        log.info("localIp:{},remoteIp:{} url:{}", localIp, remoteIp, request.getPath().value());
        int mask = getIpV4Value("255.255.255.0");
        return (mask & getIpV4Value(localIp)) == (mask & getIpV4Value(remoteIp));
    }

    /**
     * \u5c06\u70b9\u5206\u5341\u8fdb\u5236 IPv4 \u5730\u5740/\u63a9\u7801\u8f6c\u6362\u4e3a 32 \u4f4d\u6574\u6570\u503c\u3002
     * @param ipOrMask IPv4 \u5730\u5740\u6216\u63a9\u7801\u5b57\u7b26\u4e32
     * @return 32 \u4f4d\u6574\u6570\u503c\uff0c\u89e3\u6790\u5931\u8d25\u65f6\u6309\u5404\u5b57\u8282\u96f6\u503c\u7ec4\u5408\u8fd4\u56de
     */
    public static int getIpV4Value(String ipOrMask) {
        byte[] addr = getIpV4Bytes(ipOrMask);
        int address1 = addr[3] & 0xFF;
        address1 |= ((addr[2] << 8) & 0xFF00);
        address1 |= ((addr[1] << 16) & 0xFF0000);
        address1 |= ((addr[0] << 24) & 0xFF000000);
        return address1;
    }

    /**
     * \u5c06\u70b9\u5206\u5341\u8fdb\u5236 IPv4 \u5730\u5740/\u63a9\u7801\u62c6\u5206\u4e3a 4 \u5b57\u8282\u6570\u7ec4\u3002
     * @param ipOrMask IPv4 \u5730\u5740\u6216\u63a9\u7801\u5b57\u7b26\u4e32
     * @return 4 \u5b57\u8282\u6570\u7ec4\uff0c\u89e3\u6790\u5931\u8d25\u65f6\u8fd4\u56de\u5168\u96f6\u6570\u7ec4
     */
    public static byte[] getIpV4Bytes(String ipOrMask) {
        try {
            String[] addrs = ipOrMask.split("\\.");
            int length = addrs.length;
            byte[] addr = new byte[length];
            for (int index = 0; index < length; index++) {
                addr[index] = (byte) (Integer.parseInt(addrs[index]) & 0xff);
            }
            return addr;
        } catch (Exception ignored) {
        }
        return new byte[4];
    }

    /**
     * \u6309\u8bbe\u5907\u6807\u8bc6\u5934\u4f18\u5148\u7ea7\uff08IDFA/OAID/OpenUDID/IMEI/AndroidId\uff09\u63d0\u53d6\u8bbe\u5907 ID\u3002
     * @param request \u670d\u52a1\u7aef\u8bf7\u6c42
     * @return \u8bbe\u5907 ID\uff0c\u672a\u643a\u5e26\u4efb\u4f55\u6807\u8bc6\u5934\u65f6\u4e3a {@code null}
     */
    public static String getDeviceId(ServerHttpRequest request) {
        HttpHeaders headers = request.getHeaders();
        String deviceId = headers.getFirst(XHeaders.X_DEVICE_IDFA);
        if (!StringUtils.hasText(deviceId)) {
            deviceId = headers.getFirst(XHeaders.X_DEVICE_OAID);
        }
        if (!StringUtils.hasText(deviceId)) {
            deviceId = headers.getFirst(XHeaders.X_DEVICE_OPENUDID);
        }
        if (!StringUtils.hasText(deviceId)) {
            deviceId = headers.getFirst(XHeaders.X_DEVICE_IMEI);
        }
        if (!StringUtils.hasText(deviceId)) {
            deviceId = headers.getFirst(XHeaders.X_DEVICE_ANDROIDID);
        }
        if (!StringUtils.hasText(deviceId)) {
            deviceId = headers.getFirst(XHeaders.X_DEVICE_OAID);
        }
        return deviceId;
    }

}
