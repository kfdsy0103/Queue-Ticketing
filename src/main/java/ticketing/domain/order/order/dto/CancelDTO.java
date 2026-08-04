package ticketing.domain.order.order.dto;

import lombok.Builder;
import lombok.Getter;

public class CancelDTO {

	// validateCancel(트랜잭션 내 검증)이 PG 환불 호출에 필요한 값만 담아 반환하는 컨텍스트
	@Getter
	@Builder
	public static class Validated {
		String tid;
		int amount;
	}

	@Getter
	@Builder
	public static class Command {
		Long orderId;
		Long userId;

		public static Command of(Long orderId, Long userId) {
			return Command.builder()
				.orderId(orderId)
				.userId(userId)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Result {
		Long orderId;

		public Response toResponse() {
			return Response.builder()
				.orderId(orderId)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Response {
		Long orderId;
	}
}
