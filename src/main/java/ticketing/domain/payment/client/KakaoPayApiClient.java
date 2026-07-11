package ticketing.domain.payment.client;

import java.util.UUID;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import ticketing.domain.payment.client.dto.KakaoPayReady;
import ticketing.global.apiPayload.code.GeneralErrorCode;
import ticketing.global.apiPayload.exception.GeneralException;

/**
 * 카카오페이 결제 API를 모방한 Client입니다.
 */
@Slf4j
@Component
public class KakaoPayApiClient {

	/**
	 * PG 쪽 결제 고유 식별자인 tid 및 리다이렉트 경로를 반환합니다.
	 */
	public KakaoPayReady ready(Long orderId) {
		try {

			// 여기서 파라미터 세팅...
			// ready() -> approve() 호출 시 ready에서 생성된 tid가 필요한데, 이때 백엔드쪽으로 pgToken만 담아서 리다이렉트해준다.
			// 그래서 ready 단계에서 approval_url에 orderId를 쿼리 파라미터로 추가해 식별할 수 있도록 해야한다.

			// ready API 호출 시 0.5초가 걸린다고 가정
			Thread.sleep(500);

			String tid = "tid_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
			String redirectUrl = "https://리다이렉트_카카오페이_결제_경로?tid=" + tid;
			log.info("PG 결제 준비 완료. tid={}", tid);

			return KakaoPayReady.builder()
				.tid(tid)
				.redirectUrl(redirectUrl)
				.build();
		} catch (InterruptedException e) {
			log.error("PaymentClient - ready()에서 Thread.sleep() 에러 발생");
			Thread.currentThread().interrupt();
			throw new GeneralException(GeneralErrorCode.INTERNAL_SERVER_ERROR);
		}
	}
}
