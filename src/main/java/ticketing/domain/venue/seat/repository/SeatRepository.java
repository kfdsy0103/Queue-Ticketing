package ticketing.domain.venue.seat.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ticketing.domain.venue.seat.entity.Seat;

public interface SeatRepository extends JpaRepository<Seat, Long> {
}
