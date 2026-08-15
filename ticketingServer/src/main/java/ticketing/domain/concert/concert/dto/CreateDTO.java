package ticketing.domain.concert.concert.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ticketing.domain.concert.concert.entity.Concert;
import ticketing.domain.concert.concertschedule.entity.ConcertSchedule;

public class CreateDTO {

	@Getter
	@NoArgsConstructor
	public static class Request {
		@NotBlank
		String title;
		String content;
		@NotNull
		Long venueId;
		@NotEmpty
		@Valid
		List<ScheduleRequest> schedules;

		public Command toCommand() {
			return Command.builder()
				.title(title)
				.content(content)
				.venueId(venueId)
				.schedules(schedules.stream().map(ScheduleRequest::toCommand).toList())
				.build();
		}

		@Getter
		@NoArgsConstructor
		public static class ScheduleRequest {
			@NotNull
			LocalDate performanceDate;
			@NotNull
			LocalDateTime ticketOpenAt;
			@NotEmpty
			@Valid
			List<PriceRequest> prices;

			public Command.ScheduleCommand toCommand() {
				return Command.ScheduleCommand.builder()
					.performanceDate(performanceDate)
					.ticketOpenAt(ticketOpenAt)
					.prices(prices.stream().map(PriceRequest::toCommand).toList())
					.build();
			}
		}

		@Getter
		@NoArgsConstructor
		public static class PriceRequest {
			@NotNull
			Long seatGradeId;
			@NotNull
			Integer price;

			public Command.PriceCommand toCommand() {
				return Command.PriceCommand.builder()
					.seatGradeId(seatGradeId)
					.price(price)
					.build();
			}
		}
	}

	@Getter
	@Builder
	public static class Command {
		String title;
		String content;
		Long venueId;
		List<ScheduleCommand> schedules;

		@Getter
		@Builder
		public static class ScheduleCommand {
			LocalDate performanceDate;
			LocalDateTime ticketOpenAt;
			List<PriceCommand> prices;
		}

		@Getter
		@Builder
		public static class PriceCommand {
			Long seatGradeId;
			Integer price;
		}
	}

	@Getter
	@Builder
	public static class Result {
		Long concertId;
		String title;
		String content;
		Long venueId;
		List<ScheduleInfo> schedules;

		@Getter
		@Builder
		public static class ScheduleInfo {
			Long concertScheduleId;
			LocalDate performanceDate;
			LocalDateTime ticketOpenAt;

			public static ScheduleInfo from(ConcertSchedule concertSchedule) {
				return ScheduleInfo.builder()
					.concertScheduleId(concertSchedule.getId())
					.performanceDate(concertSchedule.getPerformanceDate())
					.ticketOpenAt(concertSchedule.getTicketOpenAt())
					.build();
			}
		}

		public static Result of(Concert concert, List<ConcertSchedule> concertSchedules) {
			return Result.builder()
				.concertId(concert.getId())
				.title(concert.getTitle())
				.content(concert.getContent())
				.venueId(concert.getVenue().getId())
				.schedules(concertSchedules.stream().map(ScheduleInfo::from).toList())
				.build();
		}

		public Response toResponse() {
			return Response.builder()
				.concertId(concertId)
				.title(title)
				.content(content)
				.venueId(venueId)
				.schedules(schedules)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Response {
		Long concertId;
		String title;
		String content;
		Long venueId;
		List<Result.ScheduleInfo> schedules;
	}
}
