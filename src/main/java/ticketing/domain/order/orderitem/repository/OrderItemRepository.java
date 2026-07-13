package ticketing.domain.order.orderitem.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import ticketing.domain.order.orderitem.entity.OrderItem;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

	@Query("SELECT oi FROM OrderItem oi JOIN FETCH oi.scheduleSeat WHERE oi.order.id = :orderId")
	List<OrderItem> findAllByOrderIdWithScheduleSeat(Long orderId);

	boolean existsByOrderId(Long orderId);
}
