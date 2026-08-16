package ticketing.domain.payment.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import ticketing.domain.order.order.entity.Order;
import ticketing.domain.payment.entity.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
	Optional<Payment> findByOrderId(Long orderId);

	@Query("SELECT p.order.id FROM Payment p WHERE p.status = :status AND p.createdAt < :threshold")
	List<Long> findOrderIdsByStatusAndCreatedAtBefore(Payment.PaymentStatus status, LocalDateTime threshold);

	@Query("SELECT p.order.id FROM Payment p WHERE p.status = :status AND p.updatedAt < :threshold")
	List<Long> findOrderIdsByStatusAndUpdatedAtBefore(Payment.PaymentStatus status, LocalDateTime threshold);
}
