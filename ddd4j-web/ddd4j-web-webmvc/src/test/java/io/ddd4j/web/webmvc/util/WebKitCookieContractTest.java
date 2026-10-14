package io.ddd4j.web.webmvc.util;

import javax.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebKitCookieContractTest {

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
