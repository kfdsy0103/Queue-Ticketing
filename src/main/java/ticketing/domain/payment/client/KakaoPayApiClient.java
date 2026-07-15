package ticketing.domain.payment.client;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import lombok.extern.slf4j.Slf4j;
import ticketing.domain.payment.client.dto.KakaoPayApproveRequest;
import ticketing.domain.payment.client.dto.KakaoPayApproveResponse;
import ticketing.domain.payment.client.dto.KakaoPayReadyRequest;
import ticketing.domain.payment.client.dto.KakaoPayReadyResponse;
import ticketing.global.apiPayload.code.GeneralErrorCode;
import ticketing.global.apiPayload.exception.GeneralException;

/**
 * 카카오페이 결제 API를 모방한 Client입니다. (실제 연동은 X)
 */
@Slf4j
@Component
public class KakaoPayApiClient {

	// private final RestClient restClient;

	/**
	 * PG 쪽 결제 고유 식별자인 tid 및 리다이렉트 경로를 반환합니다.
	 */
	@Retryable(
		include = Exception.class,
		exclude = {
			HttpClientErrorException.BadRequest.class,
			HttpClientErrorException.NotFound.class
		},
		maxAttempts = 3,
		backoff = @Backoff(delay = 500)
	)
	public KakaoPayReadyResponse ready(KakaoPayReadyRequest request) {
		try {

			// 여기서 파라미터 세팅...
			// ready() -> approve() 호출 시 ready에서 생성된 tid가 필요한데, 이때 백엔드쪽으로 pgToken만 담아서 리다이렉트해준다.
			// 그래서 ready 단계에서 approval_url에 orderId를 쿼리 파라미터로 추가해 식별할 수 있도록 해야한다.

			// ready API 호출 시 0.5초가 걸린다고 가정
			Thread.sleep(500);

			String tid = "tid_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
			String redirectUrl = "https://리다이렉트_카카오페이_결제_경로?tid=" + tid;
			log.info("PG 결제 준비 완료. tid={}", tid);

			return KakaoPayReadyResponse.builder()
				.tid(tid)
				.redirectUrl(redirectUrl)
				.build();
		} catch (InterruptedException e) {
			log.error("PaymentClient - ready()에서 Thread.sleep() 에러 발생", e);
			Thread.currentThread().interrupt();
			throw new GeneralException(GeneralErrorCode.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * tid와 pgToken으로 결제 승인을 요청하고, 승인 고유 번호 aid를 반환합니다.
	 */
	@Retryable(
		include = Exception.class,
		exclude = {
			HttpClientErrorException.BadRequest.class,
			HttpClientErrorException.NotFound.class
		},
		maxAttempts = 3,
		backoff = @Backoff(delay = 500)
	)
	public KakaoPayApproveResponse approve(KakaoPayApproveRequest request) {
		try {

			// approve API 호출 도 0.5초가 걸린다고 가정
			Thread.sleep(500);

			String tid = request.getTid();
			String aid = "aid_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
			log.info("PG 결제 승인 완료. (출금 완료) tid={}, aid={}", tid, aid);

			return KakaoPayApproveResponse.builder()
				.aid(aid)
				.tid(tid)
				.paymentMethodType("MONEY")
				.amount(request.getAmount())
				.approvedAt(LocalDateTime.now())
				.build();
		} catch (InterruptedException e) {
			log.error("PaymentClient - approve()에서 Thread.sleep() 에러 발생", e);
			Thread.currentThread().interrupt();
			throw new GeneralException(GeneralErrorCode.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * tid와 금액으로 결제 취소(환불)를 요청합니다.
	 * 결제 승인 이후 내부 처리(좌석 판매 등)가 실패했을 때 보상 트랜잭션으로 호출되어 방금 승인된 결제를 취소합니다.
	 */
	@Retryable(
		include = Exception.class,
		exclude = {
			HttpClientErrorException.BadRequest.class,
			HttpClientErrorException.NotFound.class
		},
		maxAttempts = 3,
		backoff = @Backoff(delay = 500)
	)
	public void cancel(String tid, int amount) {
		try {

			// cancel API 호출도 0.5초가 걸린다고 가정
			Thread.sleep(500);

			log.info("PG 결제 취소 완료. tid={}, amount={}", tid, amount);
		} catch (InterruptedException e) {
			log.error("PaymentClient - cancel()에서 Thread.sleep() 에러 발생", e);
			Thread.currentThread().interrupt();
			throw new GeneralException(GeneralErrorCode.INTERNAL_SERVER_ERROR);
		}
	}
}
