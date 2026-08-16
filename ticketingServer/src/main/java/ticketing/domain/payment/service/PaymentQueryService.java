package ticketing.domain.payment.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ticketing.domain.payment.entity.Payment;
import ticketing.domain.payment.repository.PaymentRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentQueryService {

	private final PaymentRepository paymentRepository;

	/**
	 * 승인되지 못한 채 READY로 남아있는 결제 건의 주문 ID를 조회합니다.
	 */
	public List<Long> findStaleReadyOrderIds(LocalDateTime threshold) {
		return paymentRepository.findOrderIdsByStatusAndCreatedAtBefore(
			Payment.PaymentStatus.READY,
			threshold
		);
	}

	/**
	 * 환불 결과를 반영하지 못한 채 CANCEL_REQUESTED로 남아있는 결제 건의 주문 ID를 조회합니다.
	 */
	public List<Long> findStaleCancelRequestedOrderIds(LocalDateTime threshold) {
		return paymentRepository.findOrderIdsByStatusAndUpdatedAtBefore(
			Payment.PaymentStatus.CANCEL_REQUESTED,
			threshold
		);
	}
}
