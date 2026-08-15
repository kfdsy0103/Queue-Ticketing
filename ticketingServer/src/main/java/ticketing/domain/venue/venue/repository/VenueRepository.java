package ticketing.domain.venue.venue.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ticketing.domain.venue.venue.entity.Venue;

public interface VenueRepository extends JpaRepository<Venue, Long> {
}
