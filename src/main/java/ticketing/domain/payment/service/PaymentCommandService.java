package ticketing.domain.payment.service;

import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.concert.scheduleseat.constants.ScheduleSeatRedisKeys;
import ticketing.domain.order.order.entity.Order;
import ticketing.domain.order.order.exception.OrderErrorCode;
import ticketing.domain.order.order.repository.OrderRepository;
import ticketing.domain.order.orderitem.entity.OrderItem;
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

	private static final RedisScript<Long> RELEASE_OCCUPY_SCRIPT =
		RedisScript.of(new ClassPathResource("luaScripts/release-occupy.lua"), Long.class);

	private final PaymentRepository paymentRepository;
	private final OrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final KakaoPayApiClient kakaoPayApiClient;
	private final RedisUtil redisUtil;

	/**
	 * PG ready() 성공 이후 발급받은 tid/redirectUrl로 Payment(READY)를 저장합니다.
	 */
	public void createPayment(Long orderId, Payment.PaymentMethod method, String tid, String redirectUrl) {
		Order order = orderRepository.findById(orderId)
			.orElseThrow(() -> new GeneralException(OrderErrorCode.ORDER_NOT_FOUND));

		Payment payment = Payment.builder()
			.order(order)
			.method(method)
			.status(Payment.PaymentStatus.READY)
			.tid(tid)
			.redirectUrl(redirectUrl)
			.totalPrice(order.getTotalPrice())
			.build();
		paymentRepository.save(payment);
	}

	/**
	 * 환불을 요청했으나(CANCEL_REQUESTED) 그 결과를 로컬에 반영하지 못한 채 남은 결제 건을 복구합니다.
	 * 		1. PG에서 이미 취소된 경우	-> 로컬만 취소 확정
	 * 		2. 아직 취소되지 않은 경우	-> 환불을 재시도한 뒤 취소 확정
	 */
	public void processCancelRequestedPayment(Long paymentId) {

		Payment payment = paymentRepository.findById(paymentId)
			.orElseThrow(() -> new GeneralException(PaymentErrorCode.PAYMENT_NOT_FOUND));

		// 이미 처리됨
		if (payment.getStatus() != Payment.PaymentStatus.CANCEL_REQUESTED) {
			return;
		}

		// PG에 실제 상태를 조회 (진짜 API 호출이 실패했는지, 성공은 했는데 로컬 미반영인건지)
		KakaoPayOrderResponse orderResponse = kakaoPayApiClient.order(
			KakaoPayOrderRequest.builder()
				.tid(payment.getTid())
				.build()
		);

		// 아예 호출이 실패한 경우면 다시 호출
		if (orderResponse.getStatus() != KakaoPayStatus.CANCEL_PAYMENT) {
			kakaoPayApiClient.cancel(payment.getTid(), payment.getTotalPrice());
			log.warn(
				"[PaymentCommandService] - resolveStaleCancel() 환불이 반영되지 않은 상태로 감지되어 재요청했습니다. paymentId={}, tid={}, pgStatus={}",
				paymentId, payment.getTid(), orderResponse.getStatus()
			);
		}

		Order order = payment.getOrder();
		List<OrderItem> orderItems = orderItemRepository.findAllByOrderIdWithScheduleSeat(order.getId());

		// orderItem에 연결된 좌석 cancel
		orderItems.forEach(orderItem -> orderItem.getScheduleSeat().cancel());

		// orderItem cancel
		orderItems.forEach(OrderItem::cancel);

		// payment cancel
		payment.cancel();

		// order cancel
		order.cancel();

		log.info(
			"[PaymentCommandService] - resolveStaleCancel() 반영되지 못한 환불 건의 로컬 취소를 마무리했습니다. paymentId={}, orderId={}",
			paymentId, order.getId()
		);
	}

	/**
	 * 생성된 Payment 건 중에 READY 상태로 5분 이상 남아있는 결제 건 하나를 PG사에 상태 조회하고, 환불 처리합니다.
	 * 		1. 승인이 완료된 경우 -> 환불 및 CANCELLED 처리
	 * 		2. 승인이 미완료된 경우	-> CANCELLED 처리
	 */
	public void processReadyPayment(Long paymentId) {

		Payment payment = paymentRepository.findById(paymentId)
			.orElseThrow(() -> new GeneralException(PaymentErrorCode.PAYMENT_NOT_FOUND));

		// 이미 처리됨
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

		// orderItems 만료
		orderItems.forEach(OrderItem::expire);

		// order 만료
		order.expire();

		// 주문이 종료되었으므로 재점유를 막던 점유 Key 해제 (다른 사용자가 점유했다면 해제 X)
		List<String> occupyKeys = orderItems.stream()
			.map(orderItem -> ScheduleSeatRedisKeys.occupyKey(orderItem.getScheduleSeat().getId()))
			.toList();
		if (!occupyKeys.isEmpty()) {
			redisUtil.execute(RELEASE_OCCUPY_SCRIPT, occupyKeys, order.getUser().getId().toString());
		}

		log.info(
			"[PaymentCommandService] - resolveStalePayment() 결제 건이 종료 처리되어 더 이상 완료될 수 없는 주문을 만료 처리했습니다. orderId={}",
			order.getId()
		);
	}
}
