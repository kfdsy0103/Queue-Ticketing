package ticketing.domain.seatgrade.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ticketing.domain.seatgrade.entity.SeatGrade;

public interface SeatGradeRepository extends JpaRepository<SeatGrade, Long> {
}
