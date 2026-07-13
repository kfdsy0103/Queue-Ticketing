package ticketing.domain.payment.client.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class KakaoPayApproveRequest {
	private String tid;
	private String pgToken;
	private int amount;
}
