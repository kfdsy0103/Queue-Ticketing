package ticketing.domain.queue.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.queue.dto.EnterDTO;
import ticketing.domain.queue.dto.StatusDTO;
import ticketing.domain.queue.service.QueueService;
import ticketing.global.apiPayload.CommonResponse;
import ticketing.global.apiPayload.code.GeneralSuccessCode;

@Slf4j
@RestController
@RequestMapping("/api/v1/queue")
@RequiredArgsConstructor
public class QueueController {

	private final QueueService queueService;

	@PostMapping("/enter")
	public CommonResponse<?> enter(@Valid @RequestBody EnterDTO.Request request) {
		log.info("[QueueController] enter() 호출 - userId: {}", request.getUserId());
		EnterDTO.Result result = queueService.enter(request.toCommand());
		return CommonResponse.onSuccess(GeneralSuccessCode.CREATED, result.toResponse());
	}

	@GetMapping("/status")
	public CommonResponse<?> status(@Valid @RequestBody StatusDTO.Request request) {
		log.info("[QueueController] status() 호출 - userId: {}", request.getUserId());
		StatusDTO.Result result = queueService.status(request.toCommand());
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, result.toResponse());
	}
}
