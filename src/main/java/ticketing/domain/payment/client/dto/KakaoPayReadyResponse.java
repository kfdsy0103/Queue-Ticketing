package ticketing.domain.payment.client.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class KakaoPayReadyResponse {
	private String tid;
	private String redirectUrl;
}
