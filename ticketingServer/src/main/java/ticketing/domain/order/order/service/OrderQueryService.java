package ticketing.domain.order.order.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.order.order.dto.FindDTO;
import ticketing.domain.order.order.entity.Order;
import ticketing.domain.order.order.exception.OrderErrorCode;
import ticketing.domain.order.order.repository.OrderRepository;
import ticketing.global.apiPayload.code.GeneralErrorCode;
import ticketing.global.apiPayload.exception.GeneralException;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderQueryService {

	private final OrderRepository orderRepository;

	public FindDTO.Result find(FindDTO.Command command) {
		Order order = orderRepository.findById(command.getOrderId())
			.orElseThrow(() -> new GeneralException(OrderErrorCode.ORDER_NOT_FOUND));

		// API 호출자가 Order의 소유자인지 검증
		if (!order.getUser().getId().equals(command.getUserId())) {
			throw new GeneralException(GeneralErrorCode.FORBIDDEN);
		}

		return FindDTO.Result.builder()
			.orderId(order.getId())
			.orderStatus(order.getOrderStatus())
			.totalPrice(order.getTotalPrice())
			.createdAt(order.getCreatedAt())
			.build();
	}

	/**
	 * 결제가 붙지 못한 채 PENDING으로 남아있는 주문의 ID를 조회합니다.
	 */
	public List<Long> findOrphanedPendingOrderIds(LocalDateTime threshold) {
		return orderRepository.findOrphanedPendingOrderIdsWithoutPayment(
			Order.OrderStatus.PENDING,
			threshold
		);
	}
}