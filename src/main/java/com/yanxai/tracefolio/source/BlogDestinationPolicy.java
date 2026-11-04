package com.yanxai.tracefolio.source;

import java.net.InetAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Operator-selected HTTPS hosts only. Network egress policy remains required for shared deployment. */
public class BlogDestinationPolicy {
    private final Set<String> allowedHosts;

    public BlogDestinationPolicy(String allowedHosts) {
        this.allowedHosts = Arrays.stream((allowedHosts == null ? "" : allowedHosts).split(","))
                .map(String::strip).filter(host -> !host.isBlank())
                .map(host -> host.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }

    public URI validate(String input) {
        try {
            URI uri = URI.create(input);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getRawUserInfo() != null
                    || uri.getFragment() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                    || !allowedHosts.contains(host.toLowerCase(Locale.ROOT))) {
                throw rejected();
            }
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0 || Arrays.stream(addresses).anyMatch(address -> !isPublicAddress(address))) {
                throw rejected();
            }
            return uri;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw rejected();
        }
    }

    static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] bytes = address.getAddress();
        int a = bytes[0] & 255, b = bytes[1] & 255;
        if (bytes.length == 4) {
            int c = bytes[2] & 255;
            return a != 0 && a != 10 && a != 127 && a < 224
                    && !(a == 100 && b >= 64 && b <= 127)
                    && !(a == 169 && b == 254) && !(a == 172 && b >= 16 && b <= 31)
                    && !(a == 192 && (b == 0 || b == 168))
                    && !(a == 198 && (b == 18 || b == 19 || (b == 51 && c == 100)))
                    && !(a == 203 && b == 0 && c == 113);
        }
        // Globally routable IPv6 only; exclude documentation and transition ranges.
        return (a & 0xe0) == 0x20 && !(a == 0x20 && b == 0x02)
                && !(a == 0x20 && b == 0x01 && bytes[2] == 0 && bytes[3] == 0)
                && !(a == 0x20 && b == 0x01 && (bytes[2] & 255) == 0x0d && (bytes[3] & 255) == 0xb8);
    }

    private ResponseStatusException rejected() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "博客导入仅允许服务端配置的可信 HTTPS 域名及公开目标；请配置 TRACEFOLIO_BLOG_ALLOWED_HOSTS。");
    }
}
