package ticketing.domain.order.order.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ticketing.domain.payment.entity.Payment;

public class CreateDTO {

	@Getter
	@NoArgsConstructor
	public static class Request {
		@NotEmpty
		List<Long> scheduleSeatIds;
		@NotNull
		Payment.PaymentMethod paymentMethod;

		public Command toCommand(Long userId) {
			return Command.builder()
				.userId(userId)
				.scheduleSeatIds(scheduleSeatIds)
				.paymentMethod(paymentMethod)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Command {
		Long userId;
		List<Long> scheduleSeatIds;
		Payment.PaymentMethod paymentMethod;
	}

	@Getter
	@Builder
	public static class Result {
		Long orderId;
		String redirectUrl;

		public Response toResponse() {
			return Response.builder()
				.orderId(orderId)
				.redirectUrl(redirectUrl)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Response {
		Long orderId;
		String redirectUrl;
	}
}
