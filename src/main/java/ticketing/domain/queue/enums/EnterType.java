package ticketing.domain.queue.enums;

public enum EnterType {
	NORMAL,	 // 일반적인 예매하기 버튼 호출
	RESUME,	 // '아니오, 기존 예매를 유지합니다.'
	REJOIN   // '네, 새로운 예매를 진행합니다.'
}
