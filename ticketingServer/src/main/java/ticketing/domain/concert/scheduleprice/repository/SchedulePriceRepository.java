package ticketing.domain.concert.scheduleprice.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ticketing.domain.concert.scheduleprice.entity.SchedulePrice;

public interface SchedulePriceRepository extends JpaRepository<SchedulePrice, Long> {

	Optional<SchedulePrice> findByConcertScheduleIdAndSeatGradeId(Long concertScheduleId, Long seatGradeId);

	List<SchedulePrice> findAllByConcertScheduleId(Long concertScheduleId);

	List<SchedulePrice> findAllByConcertScheduleIdIn(Collection<Long> concertScheduleIds);
}
