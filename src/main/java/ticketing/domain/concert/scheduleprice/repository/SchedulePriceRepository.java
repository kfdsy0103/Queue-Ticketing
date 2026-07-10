package ticketing.domain.concert.scheduleprice.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ticketing.domain.concert.scheduleprice.entity.SchedulePrice;

public interface SchedulePriceRepository extends JpaRepository<SchedulePrice, Long> {
}
