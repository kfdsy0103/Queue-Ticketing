package ticketing.domain.concert.scheduleseat.repository;

public interface ScheduleSeatGradeProjection {
	Long getScheduleSeatId();
	Long getSeatGradeId();
	String getSeatGradeName();
}
