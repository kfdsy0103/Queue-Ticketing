package ticketing.domain.payment.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ticketing.domain.order.order.entity.Order;
import ticketing.domain.payment.entity.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
	Optional<Payment> findByOrderId(Long orderId);
	List<Payment> findAllByStatusAndCreatedAtBefore(Payment.PaymentStatus status, LocalDateTime createdAtBefore);

	/**
	 * CANCEL_REQUESTED처럼 생성이 아니라 전이 시점이 기준인 상태를 조회할 때 사용합니다.
	 */
	List<Payment> findAllByStatusAndUpdatedAtBefore(Payment.PaymentStatus status, LocalDateTime updatedAtBefore);
}
