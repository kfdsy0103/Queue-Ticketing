package ticketing.domain.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import ticketing.domain.order.order.entity.Order;
import ticketing.fixture.OrderFixture;
import ticketing.fixture.PaymentFixture;
import ticketing.fixture.UserFixture;

/**
 * Payment의 승인·환불요청·환불 전이가 기록해야 할 필드를 함께 남기는지 검증한다.
 */
class PaymentTest {

	private static final int AMOUNT = 50_000;

	private static Order order() {
		return OrderFixture.pendingOrder(1L, UserFixture.user(1L), AMOUNT);
	}

	@Test
	void 승인되면_상태와_aid와_승인시각이_함께_기록된다() {
		// given
		Payment payment = PaymentFixture.readyPayment(1L, order(), AMOUNT);
		LocalDateTime before = LocalDateTime.now();

		// when
		payment.approve(PaymentFixture.AID);

		// then
		assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.APPROVED);
		assertThat(payment.getAid()).isEqualTo(PaymentFixture.AID);
		assertThat(payment.getApprovedAt()).isBetween(before, LocalDateTime.now());
	}

	@Test
	void 환불_요청되면_CANCEL_REQUESTED로_전이되고_tid는_보존된다() {
		// given
		Payment payment = PaymentFixture.approvedPayment(1L, order(), AMOUNT);

		// when
		payment.requestCancel();

		// then
		assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.CANCEL_REQUESTED);
		assertThat(payment.getTid()).isEqualTo(PaymentFixture.TID);
	}

	@Test
	void 환불이_확정되면_CANCELLED로_전이된다() {
		// given
		Payment payment = PaymentFixture.cancelRequestedPayment(1L, order(), AMOUNT);

		// when
		payment.cancel();

		// then
		assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.CANCELLED);
	}

	@Test
	void 환불_요청_표식이_남아야_스케쥴러가_복구_대상으로_집어낼_수_있다() {
		// given
		Payment payment = PaymentFixture.approvedPayment(1L, order(), AMOUNT);

		// when
		payment.requestCancel();

		// then
		assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.CANCEL_REQUESTED);
		assertThat(payment.getAid()).isNotNull();
		assertThat(payment.getTotalPrice()).isEqualTo(AMOUNT);
	}
}
