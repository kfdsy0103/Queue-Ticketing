package ticketing.domain.concert.scheduleprice.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ticketing.domain.concert.scheduleprice.entity.SchedulePrice;

public interface SchedulePriceRepository extends JpaRepository<SchedulePrice, Long> {

	Optional<SchedulePrice> findByConcertScheduleIdAndSeatGradeId(Long concertScheduleId, Long seatGradeId);
}
