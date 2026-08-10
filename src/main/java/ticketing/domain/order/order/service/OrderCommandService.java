package ticketing.domain.order.order.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
import ticketing.domain.concert.scheduleseat.dto.FindMyOccupyDTO;
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
import ticketing.domain.order.orderitem.repository.OrderItemRepository;
import ticketing.domain.payment.client.dto.KakaoPayApproveResponse;
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

	private final OrderRepository orderRepository;
	private final UserRepository userRepository;
	private final ScheduleSeatRepository scheduleSeatRepository;
	private final OrderItemRepository orderItemRepository;
	private final SchedulePriceRepository schedulePriceRepository;
	private final PaymentRepository paymentRepository;
	private final RedisUtil redisUtil;

	/**
	 * 주문 생성 1단계: 좌석/점유 검증 후 Order(PENDING)와 OrderItem을 저장합니다. (외부 PG 호출 없음)
	 * PG ready() 호출은 이 트랜잭션 밖에서 수행되도록 OrderFacadeService가 오케스트레이션합니다.
	 */
	public Long createOrder(CreateDTO.Command command) {
		User user = userRepository.findById(command.getUserId())
			.orElseThrow(() -> new GeneralException(UserErrorCode.USER_NOT_FOUND));

		// 좌석 수 일치 확인
		List<ScheduleSeat> scheduleSeats = scheduleSeatRepository.findAllByIdInWithScheduleAndSeatGrade(command.getScheduleSeatIds());
		if (scheduleSeats.size() != command.getScheduleSeatIds().size()) {
			throw new GeneralException(ScheduleSeatErrorCode.SCHEDULE_SEAT_NOT_FOUND);
		}

		// 이미 SOLD 처리된 좌석이 포함되어 있는지 확인
		boolean hasSoldSeat = scheduleSeats.stream()
			.anyMatch(scheduleSeat -> scheduleSeat.getSeatStatus() != ScheduleSeat.SeatStatus.AVAILABLE);
		if (hasSoldSeat) {
			throw new GeneralException(ScheduleSeatErrorCode.NOT_AVAILABLE_SEAT);
		}

		// 입력받은 모든 좌석이 본인에 의해 Redis에서 점유(선점) 중인지 확인
		List<String> occupyKeys = command.getScheduleSeatIds().stream()
			.map(ScheduleSeatRedisKeys::occupyKey)
			.toList();

		Long occupiedByMe = redisUtil.execute(
			VERIFY_OCCUPY_SCRIPT,
			occupyKeys,
			command.getUserId()
		);

		// 좌석 점유 유효 시간(5분)이 지났거나, 본인 것이 아님
		if (occupiedByMe == null || occupiedByMe != 1L) {
			throw new GeneralException(ScheduleSeatErrorCode.NOT_OCCUPIED_BY_USER);
		}

		// <회차, 등급> -> 가격 Mapping
		Set<Long> concertScheduleIds = scheduleSeats.stream()
			.map(scheduleSeat -> scheduleSeat.getConcertSchedule().getId())
			.collect(Collectors.toSet());

		Map<FindMyOccupyDTO.SchedulePriceKey, Integer> priceByScheduleAndGrade = schedulePriceRepository.findAllByConcertScheduleIdIn(concertScheduleIds).stream()
			.collect(Collectors.toMap(
				schedulePrice -> FindMyOccupyDTO.SchedulePriceKey.of(
					schedulePrice.getConcertSchedule().getId(),
					schedulePrice.getSeatGrade().getId()
				),
				SchedulePrice::getPrice
			));

		// 좌석별 가격은 Map에서 획득하여 totalPrice 누적
		List<Integer> seatPrices = scheduleSeats.stream()
			.map(scheduleSeat -> {
				Integer price = priceByScheduleAndGrade.get(FindMyOccupyDTO.SchedulePriceKey.of(
					scheduleSeat.getConcertSchedule().getId(),
					scheduleSeat.getSeat().getSeatGrade().getId()
				));
				return price;
			})
			.toList();

		int totalPrice = seatPrices.stream()
			.mapToInt(Integer::intValue)
			.sum();

		// Order 생성
		Order order = Order.builder()
			.user(user)
			.orderStatus(Order.OrderStatus.PENDING)	// 최초 생성 시에는 PENDING으로
			.totalPrice(totalPrice)
			.build();
		orderRepository.save(order);

		// Order 기반으로 나머지 OrderItem - ScheduleSeat 연결 및 생성
		List<OrderItem> orderItems = new ArrayList<>();
		for (int i = 0; i < scheduleSeats.size(); i++) {
			OrderItem orderItem = OrderItem.builder()
				.order(order)
				.scheduleSeat(scheduleSeats.get(i))
				.price(seatPrices.get(i))
				.status(OrderItem.Status.PENDING)
				.build();
			orderItems.add(orderItem);
		}
		orderItemRepository.saveAll(orderItems);

		return order.getId();
	}

	/**
	 * 아직 결제가 붙지 않은 PENDING 주문을 EXPIRED로 만료시킵니다.
	 */
	public boolean expireOrder(Long orderId) {
		Order order = orderRepository.findById(orderId)
			.orElseThrow(() -> new GeneralException(OrderErrorCode.ORDER_NOT_FOUND));

		// 이미 처리됨
		if (order.getOrderStatus() != Order.OrderStatus.PENDING) {
			return false;
		}

		// Order에 달려있는 OrderItem 조회
		List<OrderItem> orderItems = orderItemRepository.findAllByOrderIdWithScheduleSeat(orderId);

		// orderItem 만료
		orderItems.forEach(OrderItem::expire);

		// order 만료
		order.expire();

		return true;
	}

	/**
	 * 소유자/상태/금액/점유를 검증하고 approve() 호출에 필요한 값을 반환합니다. (외부 PG 호출 없음)
	 */
	public ConfirmDTO.Validated validateConfirm(ConfirmDTO.Command command) {
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

		// 만료·취소되어 더 이상 결제될 수 없는 주문인 경우
		if (order.getOrderStatus() != Order.OrderStatus.PENDING) {
			throw new GeneralException(OrderErrorCode.NOT_PENDING_ORDER);
		}

		// orderId로 생성된 결제 준비 건 조회
		Payment payment = paymentRepository.findByOrderId(order.getId())
			.orElseThrow(() -> new GeneralException(PaymentErrorCode.PAYMENT_NOT_FOUND));

		// Payment에 기록된 결제 금액과 Order 금액이 일치하는지 검증
		if (payment.getTotalPrice() != order.getTotalPrice()) {
			throw new GeneralException(PaymentErrorCode.AMOUNT_MISMATCH);
		}

		// 출금 전, 주문에 속한 좌석들을 지금도 본인이 Redis에서 점유 중인지 확인
		List<OrderItem> orderItems = orderItemRepository.findAllByOrderIdWithScheduleSeat(order.getId());

		// 항목이 하나도 없는 비정상 주문에 대한 방어.
		// verify-occupy.lua는 KEYS가 비면 루프를 돌지 않고 성공(1)을 반환하므로, 여기서 먼저 막지 않으면
		// 좌석을 하나도 검증하지 않은 채 점유 확인을 통과해 결제 승인으로 넘어간다.
		if (orderItems.isEmpty()) {
			throw new GeneralException(ScheduleSeatErrorCode.NOT_OCCUPIED_BY_USER);
		}

		List<String> occupyKeys = orderItems.stream()
			.map(orderItem -> ScheduleSeatRedisKeys.occupyKey(orderItem.getScheduleSeat().getId()))
			.toList();

		Long occupiedByMe = redisUtil.execute(
			VERIFY_OCCUPY_SCRIPT,
			occupyKeys,
			command.getUserId()
		);

		if (occupiedByMe == null || occupiedByMe != 1L) {
			throw new GeneralException(ScheduleSeatErrorCode.NOT_OCCUPIED_BY_USER);
		}

		return ConfirmDTO.Validated.builder()
			.tid(payment.getTid())
			.amount(payment.getTotalPrice())
			.build();
	}

	/**
	 * 이미 승인(출금)된 결과를 받아 Payment/ScheduleSeat/Order 상태를 확정합니다.
	 */
	public ConfirmDTO.Result completeConfirm(ConfirmDTO.Command command, KakaoPayApproveResponse approveResponse) {
		Order order = orderRepository.findById(command.getOrderId())
			.orElseThrow(() -> new GeneralException(OrderErrorCode.ORDER_NOT_FOUND));

		// 멱등 (이미 처리됨)
		if (order.getOrderStatus() == Order.OrderStatus.COMPLETED) {
			throw new GeneralException(PaymentErrorCode.ALREADY_PAID);
		}

		// 만료·취소되어 더 이상 확정될 수 없는 주문인 경우
		if (order.getOrderStatus() != Order.OrderStatus.PENDING) {
			throw new GeneralException(OrderErrorCode.NOT_PENDING_ORDER);
		}

		Payment payment = paymentRepository.findByOrderId(order.getId())
			.orElseThrow(() -> new GeneralException(PaymentErrorCode.PAYMENT_NOT_FOUND));

		// PG가 실제로 승인한 금액이 우리가 요청한 결제 금액과 일치하는지 위변조 검증
		if (approveResponse.getAmount() != payment.getTotalPrice()) {
			throw new GeneralException(PaymentErrorCode.AMOUNT_MISMATCH);
		}

		List<OrderItem> orderItems = orderItemRepository.findAllByOrderIdWithScheduleSeat(order.getId());

		// Payment 엔티티 확정
		payment.approve(approveResponse.getAid());

		// orderItems 확정
		orderItems.forEach(OrderItem::confirm);

		// orderItem 에 연결된 좌석들을 SOLD 처리
		orderItems.forEach(orderItem -> orderItem.getScheduleSeat().sell());

		// order 확정
		order.complete();

		return ConfirmDTO.Result.builder()
			.orderId(order.getId())
			.tid(approveResponse.getTid())
			.aid(approveResponse.getAid())
			.paymentMethodType(approveResponse.getPaymentMethodType())
			.amount(approveResponse.getAmount())
			.approvedAt(approveResponse.getApprovedAt())
			.build();
	}

	/**
	 * 소유자/상태를 검증하고, 환불 의도를 Payment에 먼저 기록한 뒤 PG 호출에 필요한 값을 반환합니다. (외부 PG 호출 없음)
	 * 이 트랜잭션이 커밋되어야 CANCEL_REQUESTED 표식이 남고, 이후 크래시가 나도 스케쥴러가 복구할 수 있다.
	 */
	public CancelDTO.Prepared prepareCancel(CancelDTO.Command command) {
		Order order = orderRepository.findById(command.getOrderId())
			.orElseThrow(() -> new GeneralException(OrderErrorCode.ORDER_NOT_FOUND));

		// 주문 소유자와 요청자 일치 검사
		if (!order.getUser().getId().equals(command.getUserId())) {
			throw new GeneralException(GeneralErrorCode.FORBIDDEN);
		}

		// 이미 캔슬된 주문건 (멱등)
		if (order.getOrderStatus() == Order.OrderStatus.CANCELLED) {
			throw new GeneralException(OrderErrorCode.ALREADY_CANCELLED_ORDER);
		}

		// COMPLETED인 상태의 Order만 취소 가능
		if (order.getOrderStatus() != Order.OrderStatus.COMPLETED) {
			throw new GeneralException(OrderErrorCode.ORDER_NOT_COMPLETED);
		}

		Payment payment = paymentRepository.findByOrderId(order.getId())
			.orElseThrow(() -> new GeneralException(PaymentErrorCode.PAYMENT_NOT_FOUND));

		// 앞선 취소 요청이 환불 결과를 반영하지 못한 채 남아있는 건. 스케쥴러가 정리할 때까지 재요청을 막는다
		if (payment.getStatus() == Payment.PaymentStatus.CANCEL_REQUESTED) {
			throw new GeneralException(OrderErrorCode.ORDER_IN_PROGRESS);
		}

		// 진짜 환불 API 호출 전에, '환불이 요청되었다'라고 상태 전이 (네트워크 오류나 서버 크래시로 로컬 반영에 실패한 경우, 스케쥴러가 감지할 수 있도록 하기 위함)
		payment.requestCancel();

		return CancelDTO.Prepared.builder()
			.tid(payment.getTid())
			.amount(order.getTotalPrice())
			.build();
	}

	/**
	 * 이미 환불된 결과를 받아 좌석/OrderItem/Order 상태를 취소로 확정합니다.
	 */
	public CancelDTO.Result completeCancel(CancelDTO.Command command) {
		Order order = orderRepository.findById(command.getOrderId())
			.orElseThrow(() -> new GeneralException(OrderErrorCode.ORDER_NOT_FOUND));

		Payment payment = paymentRepository.findByOrderId(order.getId())
			.orElseThrow(() -> new GeneralException(PaymentErrorCode.PAYMENT_NOT_FOUND));

		List<OrderItem> orderItems = orderItemRepository.findAllByOrderIdWithScheduleSeat(order.getId());

		// 취소할 orderItem에 연결된 좌석 상태를 SOLD -> AVAILABLE로 변경
		orderItems.forEach(orderItem -> orderItem.getScheduleSeat().cancel());

		// orderItems cancel
		orderItems.forEach(OrderItem::cancel);

		// 환불이 끝난 결제 건도 CANCELLED로 확정
		payment.cancel();

		// order cancel
		order.cancel();

		return CancelDTO.Result.builder()
			.orderId(order.getId())
			.build();
	}
}
