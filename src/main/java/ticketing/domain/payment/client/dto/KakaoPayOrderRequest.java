package ticketing.domain.payment.client.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class KakaoPayOrderRequest {
	private String tid;
}
