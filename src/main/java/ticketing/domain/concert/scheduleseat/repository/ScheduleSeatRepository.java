package ticketing.domain.concert.scheduleseat.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ticketing.domain.concert.scheduleseat.entity.ScheduleSeat;

public interface ScheduleSeatRepository extends JpaRepository<ScheduleSeat, Long> {

	List<ScheduleSeat> findAllByConcertScheduleId(Long concertScheduleId);

	/**
	 * 잔여 좌석 집계에 필요한 컬럼만 Projection으로 조회합니다.
	 */
	@Query("""
		SELECT ss.id AS scheduleSeatId,
		       ss.seatStatus AS seatStatus,
		       sg.id AS seatGradeId,
		       sg.name AS seatGradeName
		FROM ScheduleSeat ss
		    JOIN ss.seat s
		    JOIN s.seatGrade sg
		WHERE ss.concertSchedule.id = :concertScheduleId
		ORDER BY sg.id
		""")
	List<ScheduleSeatGradeProjection> findSeatGradesByConcertScheduleId(
		@Param("concertScheduleId") Long concertScheduleId
	);
}
