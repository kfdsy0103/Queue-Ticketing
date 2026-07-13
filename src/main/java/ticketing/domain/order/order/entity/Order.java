package ticketing.domain.order.order.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ticketing.domain.order.order.exception.OrderErrorCode;
import ticketing.domain.user.entity.User;
import ticketing.global.apiPayload.exception.GeneralException;
import ticketing.global.entity.BaseEntity;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    private OrderStatus orderStatus;

    private int totalPrice;

    public void complete() {
        if (this.orderStatus != OrderStatus.PENDING) {
            throw new GeneralException(OrderErrorCode.NOT_PENDING_ORDER);
        }
        this.orderStatus = OrderStatus.COMPLETED;
    }

    public void cancel() {
        if (this.orderStatus == OrderStatus.CANCELLED) {
            throw new GeneralException(OrderErrorCode.ALREADY_CANCELLED_ORDER);
        }
        this.orderStatus = OrderStatus.CANCELLED;
    }

    public void subtractPrice(int price) {
        this.totalPrice -= price;
    }

    public enum OrderStatus {
        PENDING,
        COMPLETED,
        CANCELLED
    }
}
