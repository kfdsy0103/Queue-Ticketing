package ticketing.domain.order.order.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.concert.scheduleprice.entity.SchedulePrice;
import ticketing.domain.concert.scheduleprice.exception.SchedulePriceErrorCode;
import ticketing.domain.concert.scheduleprice.repository.SchedulePriceRepository;
import ticketing.domain.concert.scheduleseat.constants.ScheduleSeatRedisKeys;
import ticketing.domain.concert.scheduleseat.entity.ScheduleSeat;
import ticketing.domain.concert.scheduleseat.exception.ScheduleSeatErrorCode;
import ticketing.domain.concert.scheduleseat.repository.ScheduleSeatRepository;
import ticketing.domain.order.order.dto.CancelDTO;
import ticketing.domain.order.order.dto.ConfirmDTO;
import ticketing.domain.order.order.dto.CreateDTO;
import ticketing.domain.order.order.entity.Order;
import ticketing.domain.order.order.exception.OrderErrorCode;
import ticketing.domain.order.order.repository.OrderRepository;
import ticketing.domain.order.orderitem.entity.OrderItem;
import ticketing.domain.order.orderitem.entity.OrderItemHistory;
import ticketing.domain.order.orderitem.repository.OrderItemHistoryRepository;
import ticketing.domain.order.orderitem.repository.OrderItemRepository;
import ticketing.domain.payment.client.KakaoPayApiClient;
import ticketing.domain.payment.client.dto.KakaoPayApproveRequest;
import ticketing.domain.payment.client.dto.KakaoPayApproveResponse;
import ticketing.domain.payment.client.dto.KakaoPayReadyRequest;
import ticketing.domain.payment.client.dto.KakaoPayReadyResponse;
import ticketing.domain.payment.entity.Payment;
import ticketing.domain.payment.exception.PaymentErrorCode;
import ticketing.domain.payment.repository.PaymentRepository;
import ticketing.domain.user.entity.User;
import ticketing.domain.user.exception.UserErrorCode;
import ticketing.domain.user.repository.UserRepository;
import ticketing.global.apiPayload.code.GeneralErrorCode;
import ticketing.global.apiPayload.exception.GeneralException;
import ticketing.global.util.RedisUtil;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = false)
public class OrderCommandService {

	private static final RedisScript<Long> VERIFY_OCCUPY_SCRIPT =
		RedisScript.of(new ClassPathResource("luaScripts/verify-occupy.lua"), Long.class);
	private static final RedisScript<Long> RELEASE_OCCUPY_SCRIPT =
		RedisScript.of(new ClassPathResource("luaScripts/release-occupy.lua"), Long.class);

	// 결제 제한 시간을 5분
	private static final Duration PAYING_TTL = Duration.ofMinutes(5);

	private final OrderRepository orderRepository;
	private final UserRepository userRepository;
	private final ScheduleSeatRepository scheduleSeatRepository;
	private final OrderItemRepository orderItemRepository;
	private final OrderItemHistoryRepository orderItemHistoryRepository;
	private final SchedulePriceRepository schedulePriceRepository;
	private final PaymentRepository paymentRepository;
	private final KakaoPayApiClient kakaoPayApiClient;
	private final RedisUtil redisUtil;

	public CreateDTO.Result create(CreateDTO.Command command) {
		User user = userRepository.findById(command.getUserId())
			.orElseThrow(() -> new GeneralException(UserErrorCode.USER_NOT_FOUND));

		// 좌석 수 일치 확인
		List<ScheduleSeat> scheduleSeats = scheduleSeatRepository.findAllById(command.getScheduleSeatIds());
		if (scheduleSeats.size() != command.getScheduleSeatIds().size()) {
			throw new GeneralException(ScheduleSeatErrorCode.SCHEDULE_SEAT_NOT_FOUND);
		}

		// 입력받은 모든 좌석이 본인에 의해 Redis에서 점유(선점) 중인지 확인
		List<String> occupyKeys = command.getScheduleSeatIds().stream()
			.map(ScheduleSeatRedisKeys::occupyKey)
			.toList();

		Long occupiedByMe = redisUtil.execute(
			VERIFY_OCCUPY_SCRIPT,
			occupyKeys,
			command.getUserId().toString()
		);

		// 좌석 점유 유효 시간(5분)이 지났거나, 본인 것이 아님
		if (occupiedByMe == null || occupiedByMe != 1L) {
			throw new GeneralException(ScheduleSeatErrorCode.NOT_OCCUPIED_BY_USER);
		}

		// 좌석별 가격 조회 및 totalPrice 누적
		List<SchedulePrice> schedulePrices = scheduleSeats.stream()
			.map(scheduleSeat -> schedulePriceRepository.findByConcertScheduleIdAndSeatGradeId(
					scheduleSeat.getConcertSchedule().getId(),
					scheduleSeat.getSeat().getSeatGrade().getId())
					.orElseThrow(() -> new GeneralException(SchedulePriceErrorCode.SCHEDULE_PRICE_NOT_FOUND))
			)
			.toList();

		int totalPrice = schedulePrices.stream()
			.mapToInt(SchedulePrice::getPrice)
			.sum();

		// Order - OrderItem - ScheduleSeat 연결을 통한 주문 생성 save
		Order order = Order.builder()
			.user(user)
			.orderStatus(Order.OrderStatus.PENDING)	// 최초 생성 시에는 PENDING으로
			.totalPrice(totalPrice)
			.build();
		orderRepository.save(order);

		List<OrderItem> orderItems = new ArrayList<>();
		for (int i = 0; i < scheduleSeats.size(); i++) {
			orderItems.add(OrderItem.builder()
				.order(order)
				.scheduleSeat(scheduleSeats.get(i))
				.price(schedulePrices.get(i).getPrice())
				.build());
		}
		orderItemRepository.saveAll(orderItems);

		// 카카오페이 결제 준비 (tid 발급 및 리다이렉트 경로 획득)
		KakaoPayReadyRequest kakaoPayReadyRequest = KakaoPayReadyRequest.builder()
			.orderId(order.getId())
			.build();
		KakaoPayReadyResponse kakaoPayReadyResponseResult = kakaoPayApiClient.ready(kakaoPayReadyRequest);

		// 결제 관련 정보 엔티티 저장
		Payment payment = Payment.builder()
			.order(order)
			.method(command.getPaymentMethod())
			.status(Payment.PaymentStatus.READY)
			.tid(kakaoPayReadyResponseResult.getTid())
			.redirectUrl(kakaoPayReadyResponseResult.getRedirectUrl())
			.totalPrice(totalPrice)
			.build();
		paymentRepository.save(payment);

		// 주문 처리 중에 재점유되지 않도록 TTL을 연장
		command.getScheduleSeatIds().forEach(scheduleSeatId ->
			redisUtil.expire(ScheduleSeatRedisKeys.occupyKey(scheduleSeatId), PAYING_TTL));

		return CreateDTO.Result.builder()
			.orderId(order.getId())
			.redirectUrl(kakaoPayReadyResponseResult.getRedirectUrl())
			.build();
	}

	public ConfirmDTO.Result confirm(ConfirmDTO.Command command) {
		Order order = orderRepository.findById(command.getOrderId())
			.orElseThrow(() -> new GeneralException(OrderErrorCode.ORDER_NOT_FOUND));

		// 주문 생성자와 API 호출자 일치 검사
		if (!order.getUser().getId().equals(command.getUserId())) {
			throw new GeneralException(GeneralErrorCode.FORBIDDEN);
		}

		// 해당 orderId 건이 이미 결제 완료된 상태인 경우
		if (order.getOrderStatus() == Order.OrderStatus.COMPLETED) {
			throw new GeneralException(PaymentErrorCode.ALREADY_PAID);
		}

		// orderId로 생성된 결제 준비 건 조회
		Payment payment = paymentRepository.findByOrderId(order.getId())
			.orElseThrow(() -> new GeneralException(PaymentErrorCode.PAYMENT_NOT_FOUND));

		// Payment에 기록된 결제 금액과 Order 금액이 일치하는지 검증
		if (payment.getTotalPrice() != order.getTotalPrice()) {
			throw new GeneralException(PaymentErrorCode.AMOUNT_MISMATCH);
		}

		// 결제 완료 대상 주문 항목 조회
		List<OrderItem> orderItems = orderItemRepository.findAllByOrderIdWithScheduleSeat(order.getId());

		// 출금 전, 주문에 속한 좌석들을 지금도 본인이 Redis에서 점유 중인지 재확인 (TTL 만료 대비)
		List<String> occupyKeys = orderItems.stream()
			.map(orderItem -> ScheduleSeatRedisKeys.occupyKey(orderItem.getScheduleSeat().getId()))
			.toList();

		Long occupiedByMe = redisUtil.execute(
			VERIFY_OCCUPY_SCRIPT,
			occupyKeys,
			command.getUserId().toString()
		);

		if (occupiedByMe == null || occupiedByMe != 1L) {
			throw new GeneralException(ScheduleSeatErrorCode.NOT_OCCUPIED_BY_USER);
		}

		// 카카오페이 결제 승인 (이 단계에서 실제 출금) - 외부 API 호출
		KakaoPayApproveRequest kakaoPayApproveRequest = KakaoPayApproveRequest.builder()
			.tid(payment.getTid())
			.pgToken(command.getPgToken())
			.amount(payment.getTotalPrice())
			.build();
		KakaoPayApproveResponse kakaoPayApproveResponseResult = kakaoPayApiClient.approve(kakaoPayApproveRequest);

		// 출금 이후의 내부 처리는 실패 시 보상 트랜잭션(결제 취소)이 필요하므로 별도로 묶어 처리
		try {
			// PG가 실제로 승인한 금액이 우리가 요청한 결제 금액과 일치하는지 검증 (위변조 방지)
			if (kakaoPayApproveResponseResult.getAmount() != payment.getTotalPrice()) {
				throw new GeneralException(PaymentErrorCode.AMOUNT_MISMATCH);
			}

			// Payment 건 APPROVED 처리 (변경 감지)
			payment.approve(kakaoPayApproveResponseResult.getAid());

			// 결제 완료된 주문에 속한 좌석들을 SOLD 처리 (변경 감지)
			orderItems.forEach(orderItem -> orderItem.getScheduleSeat().sell());

			// Order 건 최종 COMPLETED 처리 (변경 감지)
			order.complete();
		} catch (GeneralException e) {
			// 이미 나간 결제를 취소(보상)하고 원래 예외를 그대로 전파
			kakaoPayApiClient.cancel(kakaoPayApproveResponseResult.getTid(), kakaoPayApproveResponseResult.getAmount());
			throw e;
		}

		return ConfirmDTO.Result.builder()
			.orderId(order.getId())
			.tid(kakaoPayApproveResponseResult.getTid())
			.aid(kakaoPayApproveResponseResult.getAid())
			.paymentMethodType(kakaoPayApproveResponseResult.getPaymentMethodType())
			.amount(kakaoPayApproveResponseResult.getAmount())
			.approvedAt(kakaoPayApproveResponseResult.getApprovedAt())
			.build();
	}

	public CancelDTO.Result cancelAll(CancelDTO.Command command) {
		Order order = orderRepository.findById(command.getOrderId())
			.orElseThrow(() -> new GeneralException(OrderErrorCode.ORDER_NOT_FOUND));

		// 주문 소유자와 요청자 일치 검사
		if (!order.getUser().getId().equals(command.getUserId())) {
			throw new GeneralException(GeneralErrorCode.FORBIDDEN);
		}

		// 결제 완료된 주문만 전체 취소 가능
		if (order.getOrderStatus() != Order.OrderStatus.COMPLETED) {
			throw new GeneralException(OrderErrorCode.ORDER_NOT_COMPLETED);
		}

		Payment payment = paymentRepository.findByOrderId(order.getId())
			.orElseThrow(() -> new GeneralException(PaymentErrorCode.PAYMENT_NOT_FOUND));

		// 카카오페이 전체 취소(잔여 결제 금액 기준) - 외부 API 호출
		kakaoPayApiClient.cancel(payment.getTid(), order.getTotalPrice());

		// 남은 모든 주문 항목의 좌석을 AVAILABLE로 되돌리고 항목 삭제 (삭제 전 감사 이력 기록)
		List<OrderItem> orderItems = orderItemRepository.findAllByOrderIdWithScheduleSeat(order.getId());
		orderItems.forEach(orderItem -> orderItem.getScheduleSeat().cancel());

		List<OrderItemHistory> orderItemHistories = orderItems.stream()
			.map(orderItem -> OrderItemHistory.from(orderItem, OrderItemHistory.Reason.CANCELLED_ALL))
			.toList();
		orderItemHistoryRepository.saveAll(orderItemHistories);

		orderItemRepository.deleteAll(orderItems);

		// 주문이 종료되었으므로 재점유를 막던 점유 Key 해제 (그 사이 다른 사용자가 재점유했다면 건드리지 않음)
		List<String> occupyKeys = orderItems.stream()
			.map(orderItem -> ScheduleSeatRedisKeys.occupyKey(orderItem.getScheduleSeat().getId()))
			.toList();
		redisUtil.execute(RELEASE_OCCUPY_SCRIPT, occupyKeys, command.getUserId().toString());

		order.cancel();

		return CancelDTO.Result.builder()
			.orderId(order.getId())
			.build();
	}
}
