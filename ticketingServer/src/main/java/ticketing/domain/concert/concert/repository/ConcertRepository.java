package ticketing.domain.concert.concert.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ticketing.domain.concert.concert.entity.Concert;

public interface ConcertRepository extends JpaRepository<Concert, Long> {
}
