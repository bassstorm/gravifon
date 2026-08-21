package com.gravifon.player.observability;

import java.util.UUID;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void doFilter_usesIncomingCorrelationIdAndCleansMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "cid-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            assertThat(req.getAttribute(CorrelationIdFilter.CORRELATION_ID_REQUEST_ATTRIBUTE)).isEqualTo("cid-123");
            assertThat(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY)).isEqualTo("cid-123");
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).isEqualTo("cid-123");
        assertThat(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY)).isNull();
    }

    @Test
    void doFilter_generatesCorrelationIdWhenMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            String generated = (String) req.getAttribute(CorrelationIdFilter.CORRELATION_ID_REQUEST_ATTRIBUTE);
            assertThat(generated).isNotBlank();
            assertThat(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY)).isEqualTo(generated);
        };

        filter.doFilter(request, response, chain);

        String responseCorrelation = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertThat(responseCorrelation).isNotBlank();
        assertThatCode(() -> UUID.fromString(responseCorrelation)).doesNotThrowAnyException();
        assertThat(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY)).isNull();
    }

    @Test
    void doFilter_usesQueryCorrelationIdWhenHeaderMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/stream/t1");
        request.setParameter(CorrelationIdFilter.CORRELATION_ID_QUERY_PARAM, "cid-from-query");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            assertThat(req.getAttribute(CorrelationIdFilter.CORRELATION_ID_REQUEST_ATTRIBUTE)).isEqualTo("cid-from-query");
            assertThat(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY)).isEqualTo("cid-from-query");
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).isEqualTo("cid-from-query");
        assertThat(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY)).isNull();
    }

    @Test
    void doFilter_usesQueryAliasCorrelationIdWhenHeaderMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/stream/t1");
        request.setParameter(CorrelationIdFilter.CORRELATION_ID_QUERY_PARAM_ALIAS, "cid-from-query-alias");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            assertThat(req.getAttribute(CorrelationIdFilter.CORRELATION_ID_REQUEST_ATTRIBUTE)).isEqualTo("cid-from-query-alias");
            assertThat(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY)).isEqualTo("cid-from-query-alias");
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).isEqualTo("cid-from-query-alias");
        assertThat(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY)).isNull();
    }
}


