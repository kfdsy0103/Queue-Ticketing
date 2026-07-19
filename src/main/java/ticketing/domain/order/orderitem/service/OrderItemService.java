package ticketing.domain.order.orderitem.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.concert.scheduleseat.constants.ScheduleSeatRedisKeys;
import ticketing.domain.concert.scheduleseat.entity.ScheduleSeat;
import ticketing.domain.order.order.entity.Order;
import ticketing.domain.order.order.exception.OrderErrorCode;
import ticketing.domain.order.orderitem.dto.CancelPartialDTO;
import ticketing.domain.order.orderitem.entity.OrderItem;
import ticketing.domain.order.orderitem.entity.OrderItemHistory;
import ticketing.domain.order.orderitem.exception.OrderItemErrorCode;
import ticketing.domain.order.orderitem.repository.OrderItemHistoryRepository;
import ticketing.domain.order.orderitem.repository.OrderItemRepository;
import ticketing.domain.payment.client.KakaoPayApiClient;
import ticketing.domain.payment.entity.Payment;
import ticketing.domain.payment.exception.PaymentErrorCode;
import ticketing.domain.payment.repository.PaymentRepository;
import ticketing.global.apiPayload.code.GeneralErrorCode;
import ticketing.global.apiPayload.exception.GeneralException;
import ticketing.global.util.RedisUtil;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = false)
public class OrderItemService {

	private final OrderItemRepository orderItemRepository;
	private final OrderItemHistoryRepository orderItemHistoryRepository;
	private final PaymentRepository paymentRepository;
	private final KakaoPayApiClient kakaoPayApiClient;
	private final RedisUtil redisUtil;

	public CancelPartialDTO.Result cancelPartial(CancelPartialDTO.Command command) {
		OrderItem orderItem = orderItemRepository.findById(command.getOrderItemId())
			.orElseThrow(() -> new GeneralException(OrderItemErrorCode.ORDER_ITEM_NOT_FOUND));

		Order order = orderItem.getOrder();

		// 주문 소유자와 요청자 일치 검사
		if (!order.getUser().getId().equals(command.getUserId())) {
			throw new GeneralException(GeneralErrorCode.FORBIDDEN);
		}

		// 결제 완료된 주문의 항목만 부분 취소 가능
		if (order.getOrderStatus() != Order.OrderStatus.COMPLETED) {
			throw new GeneralException(OrderErrorCode.ORDER_NOT_COMPLETED);
		}

		Payment payment = paymentRepository.findByOrderId(order.getId())
			.orElseThrow(() -> new GeneralException(PaymentErrorCode.PAYMENT_NOT_FOUND));

		// 카카오페이 부분 환불 - 외부 API 호출
		kakaoPayApiClient.cancel(payment.getTid(), orderItem.getPrice());

		// 좌석을 다시 AVAILABLE로 되돌리고 주문 항목 삭제 (삭제 전 감사 이력 기록)
		ScheduleSeat scheduleSeat = orderItem.getScheduleSeat();
		scheduleSeat.cancel();

		orderItemHistoryRepository.save(OrderItemHistory.from(orderItem, OrderItemHistory.Reason.CANCELLED_PARTIAL));
		orderItemRepository.delete(orderItem);

		// 이 좌석에 대한 주문이 종료되었으므로 재점유를 막던 점유 Key 해제
		redisUtil.delete(List.of(ScheduleSeatRedisKeys.occupyKey(scheduleSeat.getId())));

		// 취소된 항목만큼 주문 금액 차감
		order.subtractPrice(orderItem.getPrice());

		// 주문에 남은 항목이 없으면 주문 자체도 취소 처리
		if (!orderItemRepository.existsByOrderId(order.getId())) {
			order.cancel();
		}

		return CancelPartialDTO.Result.builder()
			.orderItemId(orderItem.getId())
			.orderId(order.getId())
			.build();
	}
}
