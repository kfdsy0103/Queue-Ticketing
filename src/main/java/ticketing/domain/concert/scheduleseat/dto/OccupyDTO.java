package ticketing.domain.concert.scheduleseat.dto;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class OccupyDTO {

	@Getter
	@NoArgsConstructor
	public static class Request {
		@NotEmpty
		List<Long> scheduleSeatIds;
		@NotBlank
		String token;

		public Command toCommand(Long userId) {
			return Command.builder()
				.userId(userId)
				.scheduleSeatIds(scheduleSeatIds)
				.token(token)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Command {
		Long userId;
		List<Long> scheduleSeatIds;
		String token;
	}

	@Getter
	@Builder
	public static class Result {
		List<Long> scheduleSeatIds;
		LocalDateTime expiresAt;

		public static Result of(List<Long> scheduleSeatIds, LocalDateTime expiresAt) {
			return Result.builder()
				.scheduleSeatIds(scheduleSeatIds)
				.expiresAt(expiresAt)
				.build();
		}

		public Response toResponse() {
			return Response.builder()
				.scheduleSeatIds(scheduleSeatIds)
				.expiresAt(expiresAt)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Response {
		List<Long> scheduleSeatIds;
		LocalDateTime expiresAt;
	}
}
