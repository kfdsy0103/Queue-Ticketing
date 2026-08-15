package ticketing.domain.venue.seat.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import ticketing.domain.venue.seat.entity.Seat;

public interface SeatRepository extends JpaRepository<Seat, Long> {

	List<Seat> findAllByVenueId(Long venueId);
}
