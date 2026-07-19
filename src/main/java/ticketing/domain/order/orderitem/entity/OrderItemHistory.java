// 삭제(취소/정리)된 OrderItem의 감사 이력을 append-only로 남기는 엔티티
package ticketing.domain.order.orderitem.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ticketing.global.entity.BaseEntity;

@Entity
@Table(name = "order_item_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class OrderItemHistory extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long orderItemId;
	private Long orderId;
	private Long scheduleSeatId;

	private int price;

	@Enumerated(EnumType.STRING)
	private Reason reason;

	public static OrderItemHistory from(OrderItem orderItem, Reason reason) {
		return OrderItemHistory.builder()
			.orderItemId(orderItem.getId())
			.orderId(orderItem.getOrder().getId())
			.scheduleSeatId(orderItem.getScheduleSeat().getId())
			.price(orderItem.getPrice())
			.reason(reason)
			.build();
	}

	public enum Reason {
		CANCELLED_ALL,		// 결제 완료된 주문 전체 취소
		CANCELLED_PARTIAL,	// 주문 항목 부분 취소
		EXPIRED				// 좌석 점유 만료로 완료되지 못해 스케쥴러가 정리
	}
}
