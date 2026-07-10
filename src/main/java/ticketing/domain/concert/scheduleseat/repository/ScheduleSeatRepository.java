package ticketing.domain.concert.scheduleseat.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ticketing.domain.concert.scheduleseat.entity.ScheduleSeat;

public interface ScheduleSeatRepository extends JpaRepository<ScheduleSeat, Long> {
}
