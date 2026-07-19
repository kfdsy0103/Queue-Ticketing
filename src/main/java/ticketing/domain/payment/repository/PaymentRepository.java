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
}
