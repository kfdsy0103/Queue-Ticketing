package ticketing.domain.concert.scheduleseat.entity;

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
import ticketing.domain.concert.concertschedule.entity.ConcertSchedule;
import ticketing.domain.venue.seat.entity.Seat;
import ticketing.global.entity.BaseEntity;

@Entity
@Table(name = "schedule_seats")
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

    public void update(ConcertSchedule concertSchedule, Seat seat, SeatStatus seatStatus) {
        this.concertSchedule = concertSchedule;
        this.seat = seat;
        this.seatStatus = seatStatus;
    }

    public enum SeatStatus {
        AVAILABLE,
        RESERVED,
        SOLD
    }
}