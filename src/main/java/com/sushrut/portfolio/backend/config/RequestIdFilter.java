package com.sushrut.portfolio.backend.config;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Stamps every incoming request with a `requestId` in the SLF4J MDC.
 *
 * If the caller sent an `X-Request-Id` header AND it matches the safe
 * pattern below, that value is trusted (upstream nginx/CDN correlation).
 * Otherwise a fresh short UUID is generated. Either way the id is echoed
 * back in the response header so a user reporting an issue can share it.
 *
 * We whitelist chars because an attacker-controlled MDC value could inject
 * newlines / quotes into JSON log lines and corrupt the log envelope (aka
 * log injection). Even though the logback pattern escapes quotes, defense
 * in depth — reject anything not in the safe set outright.
 *
 * MDC is thread-local — cleared in `finally`, else the id leaks into
 * subsequent requests on the same worker thread. Ordered HIGHEST_PRECEDENCE
 * so it wraps every downstream filter's log lines.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter implements Filter {

    private static final String MDC_KEY = "requestId";
    private static final String HEADER  = "X-Request-Id";

    // Allow only URL-safe base64ish characters. Rejects newlines, quotes,
    // spaces — anything that could break JSON log lines or terminals.
    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest  httpReq = (HttpServletRequest)  req;
        HttpServletResponse httpRes = (HttpServletResponse) res;

        String incoming = httpReq.getHeader(HEADER);
        String id;
        if (incoming != null && SAFE_ID.matcher(incoming).matches()) {
            id = incoming;
        } else {
            id = shortUuid();
        }

        MDC.put(MDC_KEY, id);
        httpRes.setHeader(HEADER, id);
        try {
            chain.doFilter(req, res);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private static String shortUuid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}

