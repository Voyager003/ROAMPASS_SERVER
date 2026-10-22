package com.roampass_server.common.web;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.roampass_server.common.logging.MdcKeys;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 요청마다 로그 추적 열쇠를 MDC에 넣고, 요청 한 줄 로그를 남김
 * <ul>
 *   <li>{@code requestId}: 들어온 {@code X-Request-Id}가 형식에 맞으면 그대로, 없으면 생성. 응답 헤더로 돌려줌</li>
 *   <li>{@code orderNo}: 경로에 주문번호가 있으면(주문·내 SIM·어드민 상세·조치) 넣음. 웹훅·콜백은 본문을 읽은 서비스가 넣음</li>
 *   <li>요청 로그: 상태를 바꾸는 요청(GET 외)과 4xx·5xx는 INFO, 성공한 조회는 DEBUG(프론트 폴링이 잦아서). actuator는 남기지 않음</li>
 * </ul>
 * 다른 필터보다 먼저 실행해 뒤의 모든 로그에 열쇠가 붙게 함
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTracingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private static final Logger log = LoggerFactory.getLogger(RequestTracingFilter.class);
    private static final Pattern VALID_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Pattern ORDER_NO_IN_PATH = Pattern.compile("/(ORD-\\d{8}-[A-Z0-9]{8})(?:/|$)");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = requestIdOf(request);
        MDC.put(MdcKeys.REQUEST_ID, requestId);
        Matcher orderNo = ORDER_NO_IN_PATH.matcher(request.getRequestURI());
        if (orderNo.find()) {
            MDC.put(MdcKeys.ORDER_NO, orderNo.group(1));
        }
        response.setHeader(REQUEST_ID_HEADER, requestId);

        long startedAt = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            logRequest(request, response.getStatus(), (System.nanoTime() - startedAt) / 1_000_000);
            MDC.remove(MdcKeys.REQUEST_ID);
            MDC.remove(MdcKeys.ORDER_NO);
        }
    }

    private static String requestIdOf(HttpServletRequest request) {
        String incoming = request.getHeader(REQUEST_ID_HEADER);
        return incoming != null && VALID_REQUEST_ID.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();
    }

    private static void logRequest(HttpServletRequest request, int status, long elapsedMs) {
        boolean notable = !"GET".equals(request.getMethod()) || status >= 400;
        if (notable) {
            log.info("event=http_request method={} path={} status={} elapsedMs={}",
                    request.getMethod(), request.getRequestURI(), status, elapsedMs);
        } else if (log.isDebugEnabled()) {
            log.debug("event=http_request method={} path={} status={} elapsedMs={}",
                    request.getMethod(), request.getRequestURI(), status, elapsedMs);
        }
    }
}
