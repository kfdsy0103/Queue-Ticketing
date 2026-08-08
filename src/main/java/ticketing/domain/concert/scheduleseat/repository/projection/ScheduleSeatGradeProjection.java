package ticketing.domain.concert.scheduleseat.repository.projection;

public interface ScheduleSeatGradeProjection {
	Long getScheduleSeatId();
	Long getSeatGradeId();
	String getSeatGradeName();
}
