// OrderItemHistory 저장을 위한 리포지토리 (append-only)
package ticketing.domain.order.orderitem.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ticketing.domain.order.orderitem.entity.OrderItemHistory;

public interface OrderItemHistoryRepository extends JpaRepository<OrderItemHistory, Long> {
}
