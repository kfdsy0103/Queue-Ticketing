package ticketing.domain.order.orderitem.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ticketing.domain.order.orderitem.entity.OrderItem;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

	// orderItem은 ScheduleSeat과 함께 조회가 빈번하므로 fetch
	@Query("SELECT oi FROM OrderItem oi JOIN FETCH oi.scheduleSeat WHERE oi.order.id = :orderId")
	List<OrderItem> findAllByOrderIdWithScheduleSeat(@Param("orderId") Long orderId);
}
