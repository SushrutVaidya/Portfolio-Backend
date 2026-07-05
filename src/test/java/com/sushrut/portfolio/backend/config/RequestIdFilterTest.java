package com.sushrut.portfolio.backend.config;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Validate that RequestIdFilter:
 *   1. Accepts a safe incoming X-Request-Id and echoes it back.
 *   2. Rejects unsafe (log-injection) headers and generates a fresh id.
 *   3. Clears MDC in finally, so ids don't leak across requests.
 */
@ExtendWith(MockitoExtension.class)
class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void safeIncomingHeader_isTrusted() throws IOException, ServletException {
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(req.getHeader("X-Request-Id")).thenReturn("abc-DEF_123");

        filter.doFilter(req, res, chain);

        verify(res).setHeader("X-Request-Id", "abc-DEF_123");
        verify(chain, times(1)).doFilter(req, res);
        assertThat(MDC.get("requestId")).isNull(); // must be cleared post-chain
    }

    @Test
    void unsafeIncomingHeader_replacedWithGenerated() throws IOException, ServletException {
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        // Newline + quote would break the JSON log envelope. Must be rejected.
        when(req.getHeader("X-Request-Id")).thenReturn("evil\"\nline");

        filter.doFilter(req, res, chain);

        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(res).setHeader(eq("X-Request-Id"), cap.capture());
        assertThat(cap.getValue()).matches("[A-Za-z0-9._-]{12}");
    }

    @Test
    void mdcClearedEvenOnChainException() throws IOException, ServletException {
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(req.getHeader("X-Request-Id")).thenReturn(null);
        org.mockito.Mockito.doThrow(new RuntimeException("boom"))
            .when(chain).doFilter(req, res);

        try { filter.doFilter(req, res, chain); } catch (RuntimeException expected) {}
        assertThat(MDC.get("requestId")).isNull();
    }

    private static <T> T eq(T v) { return org.mockito.ArgumentMatchers.eq(v); }
}
