package ticketing.domain.concert.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import ticketing.domain.concert.entity.ConcertSchedule;

public interface ConcertScheduleRepository extends JpaRepository<ConcertSchedule, Long> {

	List<ConcertSchedule> findAllByTicketOpenAtBeforeAndPerformanceDateGreaterThanEqual(
		LocalDateTime ticketOpenAt, LocalDate performanceDate);
}
