package ticketing.domain.order.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ticketing.domain.order.order.entity.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
