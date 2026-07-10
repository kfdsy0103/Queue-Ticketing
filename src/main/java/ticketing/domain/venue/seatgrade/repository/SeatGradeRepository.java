package ticketing.domain.venue.seatgrade.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ticketing.domain.venue.seatgrade.entity.SeatGrade;

public interface SeatGradeRepository extends JpaRepository<SeatGrade, Long> {
}
