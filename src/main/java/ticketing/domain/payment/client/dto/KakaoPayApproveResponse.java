package ticketing.domain.payment.client.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class KakaoPayApprove {
	private String aid;
	private String tid;
	private String paymentMethodType;
	private int amount;
	private LocalDateTime approvedAt;
}
