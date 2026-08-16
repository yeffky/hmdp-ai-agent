package com.hmdp.config;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TraceFilter：请求链路生成/透传 traceId，写入 MDC，响应头回传，请求结束清理。
 */
class TraceFilterTest {

    private final TraceFilter filter = new TraceFilter();

    @Test
    void setsTraceIdInMdcAndResponseHeader() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        AtomicBoolean chained = new AtomicBoolean(false);
        FilterChain chain = (r, s) -> {
            assertNotNull(MDC.get("traceId"), "filter 应设置 MDC traceId");
            chained.set(true);
        };
        filter.doFilter(req, res, chain);
        assertTrue(chained.get());
        String header = res.getHeader("X-Trace-Id");
        assertNotNull(header);
        assertEquals(16, header.length());
        assertNull(MDC.get("traceId"), "finally 应清理 MDC，避免线程复用污染");
    }

    @Test
    void reusesIncomingTraceIdHeader() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Trace-Id", "abc123");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, (r, s) -> {});
        assertEquals("abc123", res.getHeader("X-Trace-Id"));
    }
}
