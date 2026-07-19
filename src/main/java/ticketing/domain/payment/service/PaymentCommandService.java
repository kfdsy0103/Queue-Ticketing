package ticketing.domain.payment.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.concert.scheduleseat.constants.ScheduleSeatRedisKeys;
import ticketing.domain.order.order.entity.Order;
import ticketing.domain.order.orderitem.entity.OrderItem;
import ticketing.domain.order.orderitem.entity.OrderItemHistory;
import ticketing.domain.order.orderitem.repository.OrderItemHistoryRepository;
import ticketing.domain.order.orderitem.repository.OrderItemRepository;
import ticketing.domain.payment.client.KakaoPayApiClient;
import ticketing.domain.payment.client.dto.KakaoPayOrderRequest;
import ticketing.domain.payment.client.dto.KakaoPayOrderResponse;
import ticketing.domain.payment.client.enums.KakaoPayStatus;
import ticketing.domain.payment.entity.Payment;
import ticketing.domain.payment.exception.PaymentErrorCode;
import ticketing.domain.payment.repository.PaymentRepository;
import ticketing.global.apiPayload.exception.GeneralException;
import ticketing.global.util.RedisUtil;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = false)
public class PaymentCommandService {

	private final PaymentRepository paymentRepository;
	private final OrderItemRepository orderItemRepository;
	private final OrderItemHistoryRepository orderItemHistoryRepository;
	private final KakaoPayApiClient kakaoPayApiClient;
	private final RedisUtil redisUtil;

	/**
	 * 생성된 Payment 건 중에 READY 상태로 5분 이상 남아있는 결제 건 하나를 PG사에 상태 조회하고, 환불 처리합니다.
	 * 		1. 승인이 완료된 경우 -> 환불 및 CANCELLED 처리
	 * 		2. 승인이 미완료된 경우	-> CANCELLED 처리
	 */
	public void resolveStalePayment(Long paymentId) {

		Payment payment = paymentRepository.findById(paymentId)
			.orElseThrow(() -> new GeneralException(PaymentErrorCode.PAYMENT_NOT_FOUND));

		if (payment.getStatus() != Payment.PaymentStatus.READY) {
			return;
		}

		KakaoPayOrderResponse orderResponse = kakaoPayApiClient.order(
			KakaoPayOrderRequest.builder()
				.tid(payment.getTid())
				.build()
		);

		if (orderResponse.getStatus() == KakaoPayStatus.SUCCESS_PAYMENT) {
			kakaoPayApiClient.cancel(payment.getTid(), payment.getTotalPrice());
			payment.cancel();
			log.warn(
				"[PaymentCommandService] - resolveStalePayment() 실제 승인 완료 상태였으나, 로컬 커밋 미반영 상태로 감지되어 환불 처리되었습니다. paymentId={}, tid={}",
				paymentId,
				payment.getTid()
			);
		} else {
			payment.cancel();
			log.info(
				"[PaymentCommandService] - resolveStalePayment() 실제 미승인 상태로 만료되어 결제 건을 종료 처리했습니다. paymentId={}, tid={}, pgStatus={}",
				paymentId, payment.getTid(),
				orderResponse.getStatus()
			);
		}

		// Order 및 OrderItem 상태도 변경 처리
		Order order = payment.getOrder();
		List<OrderItem> orderItems = orderItemRepository.findAllByOrderIdWithScheduleSeat(order.getId());

		// OrderItem에 unique가 걸려있어 이력 따로 관리
		List<OrderItemHistory> orderItemHistories = orderItems.stream()
			.map(orderItem -> OrderItemHistory.from(orderItem, OrderItemHistory.Reason.EXPIRED))
			.toList();
		orderItemHistoryRepository.saveAll(orderItemHistories);
		orderItemRepository.deleteAll(orderItems);

		// 주문이 종료되었으므로 재점유를 막던 점유 Key 해제
		List<String> occupyKeys = orderItems.stream()
			.map(orderItem -> ScheduleSeatRedisKeys.occupyKey(orderItem.getScheduleSeat().getId()))
			.toList();
		redisUtil.deleteAfterCommit(occupyKeys);

		order.cancel();

		log.info(
			"[PaymentCommandService] - resolveStalePayment() 결제 건이 종료 처리되어 더 이상 완료될 수 없는 주문을 취소 처리했습니다. orderId={}",
			order.getId()
		);
	}
}
