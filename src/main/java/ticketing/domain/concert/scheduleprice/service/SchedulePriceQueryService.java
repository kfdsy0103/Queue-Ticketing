package ticketing.domain.concert.scheduleprice.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.concert.scheduleprice.dto.FindAllDTO;
import ticketing.domain.concert.scheduleprice.dto.FindDTO;
import ticketing.domain.concert.scheduleprice.entity.SchedulePrice;
import ticketing.domain.concert.scheduleprice.exception.SchedulePriceErrorCode;
import ticketing.domain.concert.scheduleprice.repository.SchedulePriceRepository;
import ticketing.global.apiPayload.exception.GeneralException;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SchedulePriceQueryService {

	private final SchedulePriceRepository schedulePriceRepository;

	public FindDTO.Result find(FindDTO.Command command) {
		SchedulePrice schedulePrice = schedulePriceRepository.findById(command.getSchedulePriceId())
			.orElseThrow(() -> new GeneralException(SchedulePriceErrorCode.SCHEDULE_PRICE_NOT_FOUND));

		return FindDTO.Result.from(schedulePrice);
	}

	public FindAllDTO.Result findAll(FindAllDTO.Command command) {
		return FindAllDTO.Result.builder()
			.schedulePrices(schedulePriceRepository.findAllByConcertScheduleId(command.getConcertScheduleId()).stream()
				.map(FindAllDTO.Result.SchedulePriceInfo::from)
				.toList())
			.build();
	}
}
