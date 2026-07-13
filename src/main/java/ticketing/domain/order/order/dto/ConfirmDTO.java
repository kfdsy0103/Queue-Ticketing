package ticketing.domain.order.order.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class ConfirmDTO {

	@Getter
	@NoArgsConstructor
	public static class Request {
		@NotNull
		String pgToken;

		public ConfirmDTO.Command toCommand(Long userId, Long orderId) {
			return Command.builder()
				.userId(userId)
				.orderId(orderId)
				.pgToken(pgToken)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Command {
		Long userId;
		Long orderId;
		String pgToken;
	}

	@Getter
	@Builder
	public static class Result {
		Long orderId;
		String tid;
		String aid;
		String paymentMethodType;
		int amount;
		LocalDateTime approvedAt;

		public Response toResponse() {
			return Response.builder()
				.orderId(orderId)
				.tid(tid)
				.aid(aid)
				.paymentMethodType(paymentMethodType)
				.amount(amount)
				.approvedAt(approvedAt)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Response {
		Long orderId;
		String tid;
		String aid;
		String paymentMethodType;
		int amount;
		LocalDateTime approvedAt;
	}
}
