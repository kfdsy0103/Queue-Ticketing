package ticketing.domain.concert.scheduleseat.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ticketing.domain.concert.concertschedule.entity.ConcertSchedule;
import ticketing.domain.concert.scheduleseat.exception.ScheduleSeatErrorCode;
import ticketing.domain.venue.seat.entity.Seat;
import ticketing.global.apiPayload.exception.GeneralException;
import ticketing.global.entity.BaseEntity;

@Entity
// 잔여 좌석 집계 쿼리(회차 + 상태로 필터 후 좌석 등급 조인)를 위한 커버링 인덱스.
// InnoDB 보조 인덱스는 PK(id)를 암묵적으로 포함하므로, 해당 쿼리가 이 테이블에서 필요로 하는
// id와 seat_id가 모두 인덱스에 들어와 클러스터드 인덱스를 타지 않고 인덱스만 읽고 끝난다.
@Table(
    name = "schedule_seats",
    indexes = @Index(
        name = "idx_schedule_seat_schedule_status",
        columnList = "concert_schedule_id, seat_status, seat_id"
    )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ScheduleSeat extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concert_schedule_id")
    private ConcertSchedule concertSchedule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id")
    private Seat seat;

    @Enumerated(EnumType.STRING)
    private SeatStatus seatStatus;

    public void sell() {
        if (this.seatStatus != SeatStatus.AVAILABLE) {
            throw new GeneralException(ScheduleSeatErrorCode.NOT_AVAILABLE_SEAT);
        }
        this.seatStatus = SeatStatus.SOLD;
    }

    public void cancel() {
        if (this.seatStatus != SeatStatus.SOLD) {
            throw new GeneralException(ScheduleSeatErrorCode.NOT_SOLD_SEAT);
        }
        this.seatStatus = SeatStatus.AVAILABLE;
    }

    public enum SeatStatus {
        AVAILABLE,
        SOLD
    }
}