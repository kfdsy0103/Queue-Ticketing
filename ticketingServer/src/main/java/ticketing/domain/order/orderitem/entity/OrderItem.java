package ticketing.domain.order.orderitem.entity;

import jakarta.persistence.Column;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ticketing.domain.concert.scheduleseat.entity.ScheduleSeat;
import ticketing.domain.order.order.entity.Order;
import ticketing.global.entity.BaseEntity;

@Entity
@Table(
    name = "order_items",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_order_item_confirmed_schedule_seat",
        columnNames = "confirmed_schedule_seat_id"
    )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class OrderItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    // @OneToOne의 Unique로 인해 좌석을 점유한 유저가 create()를 못하는 문제
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_seat_id")
    private ScheduleSeat scheduleSeat;

    private int price;

    @Enumerated(EnumType.STRING)
    private Status status;

    /**
     * 결제가 CONFIRMED된 항목일 때만 scheduleSeatId를 갖고, 그 외에는 null인 컬럼으로, 조건부 Unique를 여기에 걺
     */
    @Column(name = "confirmed_schedule_seat_id")
    private Long confirmedScheduleSeatId;

    public void confirm() {
        this.status = Status.CONFIRMED;
        this.confirmedScheduleSeatId = scheduleSeat.getId();
    }

    public void expire() {
        this.status = Status.EXPIRED;
        this.confirmedScheduleSeatId = null;
    }

    public void cancel() {
        this.status = Status.CANCELLED;
        this.confirmedScheduleSeatId = null;
    }

    public enum Status {
        PENDING,    // 주문 생성됨, 결제 대기 중
        CONFIRMED,  // 결제 승인 완료
        EXPIRED,    // 결제 시간이 지나 종료
        CANCELLED   // 결제 완료 후 취소
    }
}
