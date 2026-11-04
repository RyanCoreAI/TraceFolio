package com.yanxai.tracefolio.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.net.InetAddress;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class BlogDestinationPolicyTest {
    @Test
    void rejectsNonPublicResolvedAddresses() throws Exception {
        for (String address : new String[]{"127.0.0.1", "10.1.2.3", "169.254.169.254", "100.64.0.1", "172.16.0.1", "192.168.1.1",
                "192.0.2.1", "198.18.0.1", "198.51.100.1", "203.0.113.1", "224.0.0.1", "::1", "fe80::1", "fc00::1", "2001:db8::1", "2002:7f00:1::"}) {
            assertThat(BlogDestinationPolicy.isPublicAddress(InetAddress.getByName(address))).as(address).isFalse();
        }
        assertThat(BlogDestinationPolicy.isPublicAddress(InetAddress.getByName("8.8.8.8"))).isTrue();
        assertThat(BlogDestinationPolicy.isPublicAddress(InetAddress.getByName("2606:4700:4700::1111"))).isTrue();
    }

    @Test
    void rejectsMalformedOrUntrustedUrlsBeforeAnyConnection() {
        var policy = new BlogDestinationPolicy("example.com");
        for (String url : new String[]{"http://example.com/", "https://example.com:8080/", "https://example.com@127.0.0.1/", "file:///etc/hosts",
                "https://example.com.evil.test/", "https://evil.test/", "https://example.com/#fragment", "not a URL"}) {
            assertThatThrownBy(() -> policy.validate(url)).as(url).isInstanceOf(ResponseStatusException.class);
        }
        assertThatThrownBy(() -> new BlogDestinationPolicy("127.0.0.1").validate("https://127.0.0.1/"))
                .isInstanceOf(ResponseStatusException.class);
    }
}
