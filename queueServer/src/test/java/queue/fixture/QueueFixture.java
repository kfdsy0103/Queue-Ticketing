package queue.fixture;

import queue.domain.queue.dto.EnterDTO;
import queue.domain.queue.dto.StatusDTO;
import queue.domain.queue.dto.TakeoverDTO;
import queue.domain.queue.enums.EnterType;

public final class QueueFixture {

	public static final String TOKEN = "token";

	private QueueFixture() {
	}

	public static EnterDTO.Command enterCommand(Long userId, Long concertScheduleId, EnterType enterType) {
		return EnterDTO.Command.builder()
			.userId(userId)
			.concertScheduleId(concertScheduleId)
			.enterType(enterType)
			.build();
	}

	public static StatusDTO.Command statusCommand(Long userId) {
		return StatusDTO.Command.of(userId, TOKEN);
	}

	public static TakeoverDTO.Command takeoverCommand(Long userId, Long concertScheduleId) {
		return TakeoverDTO.Command.builder()
			.userId(userId)
			.concertScheduleId(concertScheduleId)
			.build();
	}
}
