package io.ddd4j.spring.util;

import io.ddd4j.core.XHeaders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class WebFluxKit {

    private static final String XML_HTTP_REQUEST = "XMLHttpRequest";
    private static final String X_REQUESTED_WITH = "X-Requested-With";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String[] xheaders = new String[]{"X-Forwarded-For", "x-forwarded-for"};
    private static final String[] headers = new String[]{"Cdn-Src-Ip", "Proxy-Client-IP", "WL-Proxy-Client-IP", "X-Real-IP", "HTTP_CLIENT_IP", "HTTP_X_FORWARDED_FOR"};
    private static final String LOCAL_HOST = "localhost";
    private static final String LOCAL_IP6 = "0:0:0:0:0:0:0:1";
    private static final String LOCAL_IP = "127.0.0.1";
    private static final String UNKNOWN = "unknown";

    public static boolean isAjaxResponse(ServerHttpRequest request) {
        return isAjaxRequest(request) || isContentTypeJson(request) || isPostRequest(request);
    }

    public static boolean isObjectRequest(ServerHttpRequest request) {
        return isPostRequest(request) && isContentTypeJson(request);
    }

    public static boolean isObjectRequest(HttpRequest request) {
        return isPostRequest(request) && isContentTypeJson(request);
    }

    public static boolean isAjaxRequest(ServerHttpRequest request) {
        return XML_HTTP_REQUEST.equals(request.getHeaders().getFirst(X_REQUESTED_WITH));
    }

    public static boolean isAjaxRequest(HttpRequest request) {
        return Objects.requireNonNull(request.getHeaders().get(X_REQUESTED_WITH)).contains(XML_HTTP_REQUEST);
    }

    public static boolean isContentTypeJson(ServerHttpRequest request) {
        return Objects.requireNonNull(request.getHeaders().get(HttpHeaders.CONTENT_TYPE)).contains(CONTENT_TYPE_JSON);
    }

    public static boolean isContentTypeJson(HttpRequest request) {
        return Objects.requireNonNull(request.getHeaders().get(HttpHeaders.CONTENT_TYPE)).contains(CONTENT_TYPE_JSON);
    }

    public static boolean isPostRequest(ServerHttpRequest request) {
        return HttpMethod.POST.compareTo(request.getMethod()) == 0;
    }

    public static boolean isPostRequest(HttpRequest request) {
        return HttpMethod.POST.compareTo(request.getMethod()) == 0;
    }

    /**
     * 从 Flux<DataBuffer> 中获取请求体字符串。
     */
    public static String resolveBodyFromRequest(ServerHttpRequest serverHttpRequest) {
        if (serverHttpRequest.getHeaders().getContentLength() == 0) {
            return org.apache.commons.lang3.StringUtils.EMPTY;
        }
        Flux<DataBuffer> body = serverHttpRequest.getBody();
        AtomicReference<String> bodyRef = new AtomicReference<>();
        body.subscribe(buffer -> {
            byte[] bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            DataBufferUtils.release(buffer);
            bodyRef.set(new String(bytes, StandardCharsets.UTF_8));
        });
        return bodyRef.get();
    }

    /**
     * 获取请求客户端 IP 地址，支持代理服务器。
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

    public static boolean isSameSegment(ServerHttpRequest request) {
        String localIp = Objects.requireNonNull(request.getLocalAddress()).getAddress().getHostAddress();
        String remoteIp = getRemoteAddr(request);
        log.info("localIp:{},remoteIp:{} url:{}", localIp, remoteIp, request.getPath().value());
        int mask = getIpV4Value("255.255.255.0");
        return (mask & getIpV4Value(localIp)) == (mask & getIpV4Value(remoteIp));
    }

    public static int getIpV4Value(String ipOrMask) {
        byte[] addr = getIpV4Bytes(ipOrMask);
        int address1 = addr[3] & 0xFF;
        address1 |= ((addr[2] << 8) & 0xFF00);
        address1 |= ((addr[1] << 16) & 0xFF0000);
        address1 |= ((addr[0] << 24) & 0xFF000000);
        return address1;
    }

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
