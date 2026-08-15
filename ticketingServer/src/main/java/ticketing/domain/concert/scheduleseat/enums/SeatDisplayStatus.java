package ticketing.domain.concert.scheduleseat.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * DTO 표시용
 */
@Getter
@RequiredArgsConstructor
public enum SeatDisplayStatus {
    AVAILABLE("예약 가능한 좌석"),
    OCCUPIED("다른 사용자가 점유 중인 좌석"),
    SOLD("결제가 완료되어 판매된 좌석");

    private final String description;
}
