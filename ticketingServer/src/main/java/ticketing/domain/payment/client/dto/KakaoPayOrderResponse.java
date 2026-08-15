package ticketing.domain.payment.client.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;
import ticketing.domain.payment.client.enums.KakaoPayStatus;

@Getter
@Builder
public class KakaoPayOrderResponse {
	private String tid;
	private String aid;
	private KakaoPayStatus status;
	private String paymentMethodType;
	private int amount;
	private LocalDateTime approvedAt;
}
