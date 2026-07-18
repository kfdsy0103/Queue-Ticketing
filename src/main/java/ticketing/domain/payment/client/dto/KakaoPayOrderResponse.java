package ticketing.domain.payment.client.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;
import ticketing.domain.payment.client.enums.KakaoPayStatus;

@Getter
@Builder
public class KakaoPayOrderResponse {
	private String tid;
	private KakaoPayStatus status;
	private int amount;
	private LocalDateTime approvedAt;
}
