package ticketing.domain.concert.scheduleseat.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ticketing.domain.concert.scheduleseat.entity.ScheduleSeat;
import ticketing.domain.concert.scheduleseat.projection.ScheduleSeatGradeProjection;
import ticketing.domain.concert.scheduleseat.projection.ScheduleSeatStatusProjection;

public interface ScheduleSeatRepository extends JpaRepository<ScheduleSeat, Long> {

	/**
	 * 좌석 배치도 조회에 필요한 컬럼만 Projection으로 조회합니다.
	 * seat_id는 FK 컬럼이라 조인이 필요 없어, idx_schedule_seat_schedule_status 인덱스만 읽고 끝납니다.
	 */
	@Query("""
		SELECT ss.id AS scheduleSeatId,
		       ss.seat.id AS seatId,
		       ss.seatStatus AS seatStatus
		FROM ScheduleSeat ss
		WHERE ss.concertSchedule.id = :concertScheduleId
		""")
	List<ScheduleSeatStatusProjection> findSeatStatusesByConcertScheduleId(
		@Param("concertScheduleId") Long concertScheduleId
	);

	/**
	 * 잔여 좌석 집계에 필요한 컬럼만, 지정한 상태의 좌석에 한해 Projection으로 조회합니다.
	 */
	@Query("""
		SELECT ss.id AS scheduleSeatId,
		       sg.id AS seatGradeId,
		       sg.name AS seatGradeName
		FROM ScheduleSeat ss
		    JOIN ss.seat s
		    JOIN s.seatGrade sg
		WHERE ss.concertSchedule.id = :concertScheduleId
		  AND ss.seatStatus = :seatStatus
		ORDER BY sg.id
		""")
	List<ScheduleSeatGradeProjection> findAvailableSeatsByConcertScheduleIdAndSeatStatus(
		@Param("concertScheduleId") Long concertScheduleId,
		@Param("seatStatus") ScheduleSeat.SeatStatus seatStatus
	);

	/**
	 * 점유 검증용 조회입니다. 좌석 ID와 회차를 함께 조건에 걸어, 다른 회차의 좌석이 섞여 들어오면
	 * 결과 개수가 줄어들어 호출부에서 걸러낼 수 있습니다.
	 */
	@Query("""
		SELECT ss
		FROM ScheduleSeat ss
		WHERE ss.id IN :scheduleSeatIds
		  AND ss.concertSchedule.id = :concertScheduleId
		""")
	List<ScheduleSeat> findAllByIdInAndConcertScheduleId(
		@Param("scheduleSeatIds") Collection<Long> scheduleSeatIds,
		@Param("concertScheduleId") Long concertScheduleId
	);

	/**
	 * 회차, 좌석 번호, 등급까지 함께 필요한 경우를 위해 연관 엔티티를 fetch join으로 한 번에 조회합니다.
	 */
	@Query("""
		SELECT ss
		FROM ScheduleSeat ss
		    JOIN FETCH ss.concertSchedule
		    JOIN FETCH ss.seat s
		    JOIN FETCH s.seatGrade
		WHERE ss.id IN :scheduleSeatIds
		""")
	List<ScheduleSeat> findAllByIdInWithScheduleAndSeatGrade(
		@Param("scheduleSeatIds") Collection<Long> scheduleSeatIds
	);
}
