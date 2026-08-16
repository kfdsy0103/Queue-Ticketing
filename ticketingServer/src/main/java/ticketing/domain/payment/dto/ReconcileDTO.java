package ticketing.domain.payment.dto;

import lombok.Builder;
import lombok.Getter;

public class ReconcileDTO {

	@Getter
	@Builder
	public static class Prepared {
		String tid;
		int amount;
	}
}
