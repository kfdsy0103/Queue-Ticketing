package ticketing.domain.payment.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ticketing.domain.order.order.entity.Order;
import ticketing.global.entity.BaseEntity;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Payment extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_id")
	private Order order;

	@Enumerated(EnumType.STRING)
	private PaymentMethod method;

	@Enumerated(EnumType.STRING)
	private PaymentStatus status;

	private String tid;

	private String redirectUrl;

	private int totalPrice;

	private String aid;

	private LocalDateTime approvedAt;

	public void approve(String aid) {
		this.aid = aid;
		this.status = PaymentStatus.APPROVED;
		this.approvedAt = LocalDateTime.now();
	}

	public enum PaymentMethod {
		KAKAO_PAY
	}

	public enum PaymentStatus {
		READY,
		APPROVED,
		CANCELLED
	}
}
