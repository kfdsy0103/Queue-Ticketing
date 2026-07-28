package ticketing.domain.order.order.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import ticketing.domain.order.order.entity.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {

	// Payment가 아직 붙지 않은 오래된 PENDING 주문 조회
	@Query("SELECT o FROM Order o WHERE o.orderStatus = :status AND o.createdAt < :threshold "
		+ "AND NOT EXISTS (SELECT 1 FROM Payment p WHERE p.order = o)")
	List<Order> findStalePendingOrdersWithoutPayment(Order.OrderStatus status, LocalDateTime threshold);
}
