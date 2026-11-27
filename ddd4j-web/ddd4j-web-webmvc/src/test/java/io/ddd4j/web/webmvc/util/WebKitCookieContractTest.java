package io.ddd4j.web.webmvc.util;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

/** \u901a\u8fc7 <code>request.setCookies()</code>\u3001<code>WebKit.getCookieVal()</code>\u3001<code>WebKit.getCookie()</code> \u9a8c\u8bc1 <code>WebKitCookieContract</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class WebKitCookieContractTest {

    /** \u9a8c\u8bc1<code>WebKit.getCookieVal(request, "session")</code> \u7b49\u4e8e <code>"token"</code>\uff0c<code>WebKit.getCookie(request, "session").getValue()</code> \u7b49\u4e8e <code>"token"</code>\uff0c<code>removed.getMaxAge()</code> \u7b49\u4e8e <code>0</code>\u3002 */
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
