package ticketing.domain.schedule.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ticketing.domain.concertVenue.entity.ConcertVenueSeat;

@Entity
@Table(name = "schedule_seats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduleSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concert_schedule_id")
    private ConcertSchedule concertSchedule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id")
    private ConcertVenueSeat concertVenueSeat;
}
