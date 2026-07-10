package ticketing.domain.order.orderitem.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ticketing.domain.order.orderitem.entity.OrderItem;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
}
