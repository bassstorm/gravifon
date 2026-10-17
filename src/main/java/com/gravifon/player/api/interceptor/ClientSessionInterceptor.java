package com.gravifon.player.api.interceptor;

import com.gravifon.player.session.model.ClientSessionId;
import com.gravifon.player.session.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class ClientSessionInterceptor implements HandlerInterceptor {
    public static final String CALLER_ATTRIBUTE = "gravifon.caller";
    private static final String CLIENT_SESSION_HEADER = "X-Client-Session";
    private static final String SESSION_QUERY_PARAMETER = "session";
    private static final String INVALID_PRESENTATION_MESSAGE = "Invalid client session presentation";
    private static final String SESSION_REQUIRED_MESSAGE = "A valid client session is required";
    private final SessionService sessions;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!isApiRequest(path)) {
            return true;
        }

        String[] queryValues = request.getParameterValues(SESSION_QUERY_PARAMETER);
        boolean hasQueryPresentation = queryValues != null;
        boolean streamRequest = isMediaStreamRequest(request.getMethod(), path);
        if (hasQueryPresentation && !streamRequest) {
            throw invalidPresentation();
        }
        String queryPresentation = hasQueryPresentation ? exactlyOne(queryValues) : null;
        String headerPresentation = exactlyOneHeader(request);

        if (isAcquisitionRequest(request.getMethod(), path)) {
            return true;
        }
        if (headerPresentation != null && queryPresentation != null && !headerPresentation.equals(queryPresentation)) {
            throw invalidPresentation();
        }

        String sessionId = headerPresentation != null ? headerPresentation : queryPresentation;
        if (!StringUtils.hasText(sessionId)) {
            throw sessionRequired();
        }

        var caller = sessions
            .validateAndRenew(ClientSessionId.of(sessionId))
            .orElseThrow(ClientSessionInterceptor::sessionRequired);
        request.setAttribute(CALLER_ATTRIBUTE, caller);
        return true;
    }

    private String exactlyOneHeader(HttpServletRequest request) {
        var values = Collections.list(request.getHeaders(CLIENT_SESSION_HEADER));
        if (values.size() > 1) {
            throw invalidPresentation();
        }
        return values.isEmpty() ? null : values.getFirst();
    }

    private String exactlyOne(String[] values) {
        if (values.length != 1) {
            throw invalidPresentation();
        }
        return values[0];
    }

    private boolean isApiRequest(String path) {
        return path.equals("/api") || path.startsWith("/api/");
    }

    private boolean isAcquisitionRequest(String method, String path) {
        return "POST".equals(method) && "/api/sessions".equals(path);
    }

    private boolean isMediaStreamRequest(String method, String path) {
        if (!"GET".equals(method) || !path.startsWith("/api/stream/")) {
            return false;
        }
        String trackId = path.substring("/api/stream/".length());
        return !trackId.isEmpty() && !trackId.contains("/");
    }

    private static ResponseStatusException invalidPresentation() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_PRESENTATION_MESSAGE);
    }

    private static ResponseStatusException sessionRequired() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, SESSION_REQUIRED_MESSAGE);
    }
}
