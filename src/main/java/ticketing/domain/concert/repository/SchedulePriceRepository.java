package ticketing.domain.concert.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ticketing.domain.concert.entity.SchedulePrice;

public interface SchedulePriceRepository extends JpaRepository<SchedulePrice, Long> {
}
