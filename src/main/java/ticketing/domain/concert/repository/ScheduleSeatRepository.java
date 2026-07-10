package ticketing.domain.concert.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ticketing.domain.concert.entity.ScheduleSeat;

public interface ScheduleSeatRepository extends JpaRepository<ScheduleSeat, Long> {
}
