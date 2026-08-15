package ticketing.domain.payment.client.enums;

public enum KakaoPayStatus {
	READY,									// 결제 준비 완료
	IN_PROGRESS,							// 결제 진행중 (카드 등 다른 결제수단 인증 완료, 승인 대기)
	NOT_YET_RECEIVED_SUBSCRIPTION_PAYMENT,	// 정기 결제 승인 대기
	SUCCESS_PAYMENT,						// 결제 성공
	CANCEL_PAYMENT,							// 결제 취소
	QUASI_CANCEL_PAYMENT,					// 부분 취소
	FAIL_PAYMENT,							// 결제 실패
	FAIL_AUTH_PAYMENT						// 결제 인증 실패
}
