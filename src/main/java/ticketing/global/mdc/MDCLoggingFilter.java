package ticketing.global.mdc;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class MDCLoggingFilter extends OncePerRequestFilter {

	private static final String REQUEST_ID = "requestId";
	private static final String USER_ID = "userId";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
		throws ServletException, IOException {

		// request에 대한 UUID 부여
		MDC.put(REQUEST_ID, UUID.randomUUID().toString().substring(0, 8));

		// 실제 프로젝트에서는 SecurityContextHolder에서 userId를 꺼내는 로직으로 수정 필요.
		MDC.put(USER_ID, request.getParameter("userId") != null ? request.getParameter("userId") : "anonymous");

		try {
			filterChain.doFilter(request, response);
		} finally {
			MDC.clear();    // 스레드 로컬 단위로 MDC가 관리되므로 반드시 clear() 해줘야 한다.
		}
	}
}
