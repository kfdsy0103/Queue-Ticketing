package ticketing.global.mdc;

import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class MDCLoggingInterceptor implements HandlerInterceptor {

    private static final String REQUEST_ID = "requestId";
    private static final String USER_ID = "userId";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {

        // request에 대한 UUID 부여
        MDC.put(REQUEST_ID, UUID.randomUUID().toString().substring(0, 8));

        // 실제 프로젝트에서는 SecurityContextHolder에서 userId를 꺼내는 로직으로 수정 필요.
        MDC.put(USER_ID, request.getParameter("userId") != null ? request.getParameter("userId") : "anonymous");

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        MDC.clear();    // 스레드 로컬 단위로 MDC가 관리되므로 반드시 clear() 해줘야 한다.
    }
}
