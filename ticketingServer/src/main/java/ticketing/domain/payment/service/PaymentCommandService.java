package ticketing.domain.payment.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ticketing.domain.order.order.entity.Order;
import ticketing.domain.order.order.exception.OrderErrorCode;
import ticketing.domain.order.order.repository.OrderRepository;
import ticketing.domain.order.orderitem.entity.OrderItem;
import ticketing.domain.order.orderitem.repository.OrderItemRepository;
import ticketing.domain.payment.dto.ReconcileDTO;
import ticketing.domain.payment.entity.Payment;
import ticketing.domain.payment.exception.PaymentErrorCode;
import ticketing.domain.payment.repository.PaymentRepository;
import ticketing.global.apiPayload.exception.GeneralException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = false)
public class PaymentCommandService {

	private final PaymentRepository paymentRepository;
	private final OrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;

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

	public ReconcileDTO.Prepared prepareReadyReconcile(Long orderId) {

		Payment payment = paymentRepository.findByOrderId(orderId)
			.orElseThrow(() -> new GeneralException(PaymentErrorCode.PAYMENT_NOT_FOUND));

		if (payment.getStatus() != Payment.PaymentStatus.READY) {
			throw new GeneralException(PaymentErrorCode.NOT_RECONCILE_TARGET);
		}

		return ReconcileDTO.Prepared.builder()
			.tid(payment.getTid())
			.amount(payment.getTotalPrice())
			.build();
	}

	public void completeReadyReconcile(Long orderId) {

		Payment payment = paymentRepository.findByOrderId(orderId)
			.orElseThrow(() -> new GeneralException(PaymentErrorCode.PAYMENT_NOT_FOUND));

		if (payment.getStatus() != Payment.PaymentStatus.READY) {
			throw new GeneralException(PaymentErrorCode.RECONCILE_STATE_CHANGED);
		}

		payment.cancel();

		Order order = payment.getOrder();
		List<OrderItem> orderItems = orderItemRepository.findAllByOrderIdWithScheduleSeat(order.getId());

		orderItems.forEach(OrderItem::expire);
		order.expire();
	}

	public ReconcileDTO.Prepared prepareCancelReconcile(Long orderId) {

		Payment payment = paymentRepository.findByOrderId(orderId)
			.orElseThrow(() -> new GeneralException(PaymentErrorCode.PAYMENT_NOT_FOUND));

		// 이미 처리됨
		if (payment.getStatus() != Payment.PaymentStatus.CANCEL_REQUESTED) {
			throw new GeneralException(PaymentErrorCode.NOT_RECONCILE_TARGET);
		}

		return ReconcileDTO.Prepared.builder()
			.tid(payment.getTid())
			.amount(payment.getTotalPrice())
			.build();
	}

	public void completeCancelReconcile(Long orderId) {

		Payment payment = paymentRepository.findByOrderId(orderId)
			.orElseThrow(() -> new GeneralException(PaymentErrorCode.PAYMENT_NOT_FOUND));

		if (payment.getStatus() != Payment.PaymentStatus.CANCEL_REQUESTED) {
			throw new GeneralException(PaymentErrorCode.RECONCILE_STATE_CHANGED);
		}

		Order order = payment.getOrder();
		List<OrderItem> orderItems = orderItemRepository.findAllByOrderIdWithScheduleSeat(order.getId());

		// SOLD였던 좌석을 다시 AVAILABLE로 복구시킴
		orderItems.forEach(orderItem -> orderItem.getScheduleSeat().cancel());

		orderItems.forEach(OrderItem::cancel);
		payment.cancel();
		order.cancel();
	}
}
