package com.gravifon.player.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "CorrelationId";
    public static final String CORRELATION_ID_QUERY_PARAM = "cid";
    public static final String CORRELATION_ID_QUERY_PARAM_ALIAS = "CorrelationId";
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";
    public static final String CORRELATION_ID_REQUEST_ATTRIBUTE = "gravifon.correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String incoming = request.getHeader(CORRELATION_ID_HEADER);
        String incomingFromQuery = firstNonBlank(
                request.getParameter(CORRELATION_ID_QUERY_PARAM),
                request.getParameter(CORRELATION_ID_QUERY_PARAM_ALIAS));
        String correlationId;
        if (StringUtils.hasText(incoming)) {
            correlationId = incoming;
        } else if (StringUtils.hasText(incomingFromQuery)) {
            correlationId = incomingFromQuery;
        } else {
            correlationId = UUID.randomUUID().toString();
            if (isApiRequest(request)) {
                log.warn(
                        "Missing CorrelationId header/query for API request {}. Generated fallback correlation id: {}",
                        request.getRequestURI(),
                        correlationId);
            } else {
                log.debug(
                        "Missing CorrelationId for non-API request {}. Generated fallback correlation id: {}",
                        request.getRequestURI(),
                        correlationId);
            }
        }

        request.setAttribute(CORRELATION_ID_REQUEST_ATTRIBUTE, correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CORRELATION_ID_MDC_KEY);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static boolean isApiRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && path.startsWith("/api/");
    }
}
