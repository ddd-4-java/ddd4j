package io.ddd4j.web.webmvc.util;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 通过 <code>request.setCookies()</code>、<code>WebKit.getCookieVal()</code>、<code>WebKit.getCookie()</code> 验证 <code>WebKitCookieContract</code> 的运行行为。
 */
class WebKitCookieContractTest {

    /**
     * 验证<code>WebKit.getCookieVal(request, "session")</code> 等于 <code>"token"</code>，<code>WebKit.getCookie(request, "session").getValue()</code> 等于 <code>"token"</code>，<code>removed.getMaxAge()</code> 等于 <code>0</code>。
     */
    @Test
    void readsWritesAndRemovesCookieAcrossServletNamespace() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("session", "token"));
        assertEquals("token", WebKit.getCookieVal(request, "session"));
        assertEquals("token", WebKit.getCookie(request, "session").getValue());
        assertNull(WebKit.getCookie(request, "absent"));

        MockHttpServletResponse response = new MockHttpServletResponse();
        WebKit.setCookie(response, "session", "token", 60);
        Cookie written = response.getCookie("session");
        assertEquals("token", written.getValue());
        assertEquals("/", written.getPath());
        assertEquals(60, written.getMaxAge());
        assertTrue(written.isHttpOnly());

        WebKit.removeCookie(response, "session");
        assertEquals(2, response.getCookies().length);
        Cookie removed = response.getCookies()[1];
        assertNull(removed.getValue());
        assertEquals(0, removed.getMaxAge());
    }
}
