package io.ddd4j.data.external.region;

import java.util.Objects;
import java.util.regex.Pattern;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * IP address helpers independent of Boot starters.
 */
public final class IpAddressKit {

    private static final Pattern IPV4 = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)$");

    private IpAddressKit() {
    }

    public static boolean isIpv4(String ip) {
        return Objects.nonNull(ip) && IPV4.matcher(ip).matches();
    }

    public static boolean internalIp(String ip) {
        if (!isIpv4(ip)) {
            return false;
        }
        String[] parts = ip.split("\\.");
        int first = Integer.parseInt(parts[0]);
        int second = Integer.parseInt(parts[1]);
        return first == 10
                || first == 127
                || first == 0
                || first == 169 && second == 254
                || first == 172 && second >= 16 && second <= 31
                || first == 192 && second == 168;
    }

    public static long ip2long(String ip) {
        String[] parts = ip.split("\\.");
        long result = 0L;
        for (String part : parts) {
            result = result << 8 | Integer.parseInt(part);
        }
        return result;
    }

    public static String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }

    public static String getHostAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return "127.0.0.1";
        }
    }
}
