package ticketing.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ticketing.domain.payment.entity.Payment;
import ticketing.domain.payment.repository.PaymentRepository;

/**
 * 정리 대상 조회 두 건이 각각 올바른 결제 상태와 시각 기준(createdAt / updatedAt)으로 질의하는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class PaymentQueryServiceTest {

	private static final LocalDateTime THRESHOLD = LocalDateTime.of(2026, 8, 26, 12, 0);

	@Mock
	private PaymentRepository paymentRepository;

	@InjectMocks
	private PaymentQueryService paymentQueryService;

	@Test
	void 승인되지_못한_결제는_READY_상태와_생성시각_기준으로_조회한다() {
		// given
		given(paymentRepository.findOrderIdsByStatusAndCreatedAtBefore(Payment.PaymentStatus.READY, THRESHOLD))
			.willReturn(List.of(1L, 2L));

		// when
		List<Long> orderIds = paymentQueryService.findStaleReadyOrderIds(THRESHOLD);

		// then
		assertThat(orderIds).containsExactly(1L, 2L);
		verify(paymentRepository).findOrderIdsByStatusAndCreatedAtBefore(Payment.PaymentStatus.READY, THRESHOLD);
	}

	@Test
	void 환불_결과를_반영하지_못한_결제는_CANCEL_REQUESTED_상태와_수정시각_기준으로_조회한다() {
		// given
		given(paymentRepository.findOrderIdsByStatusAndUpdatedAtBefore(Payment.PaymentStatus.CANCEL_REQUESTED, THRESHOLD))
			.willReturn(List.of(3L));

		// when
		List<Long> orderIds = paymentQueryService.findStaleCancelRequestedOrderIds(THRESHOLD);

		// then
		assertThat(orderIds).containsExactly(3L);
		verify(paymentRepository).findOrderIdsByStatusAndUpdatedAtBefore(Payment.PaymentStatus.CANCEL_REQUESTED, THRESHOLD);
	}
}
