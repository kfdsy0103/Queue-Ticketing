package ticketing.domain.payment.client.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class KakaoPayOrderRequest {
	private String tid;

	// 실제 주문조회 API는 tid만 보내고 금액은 응답으로 받는다.
	// 모방 Client라 반환할 금액의 출처가 없어 요청으로 받아 그대로 돌려준다.
	private int amount;
}
