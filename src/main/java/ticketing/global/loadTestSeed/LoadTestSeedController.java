package ticketing.global.loadTestSeed;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.global.apiPayload.CommonResponse;
import ticketing.global.apiPayload.code.GeneralSuccessCode;

@Slf4j
@Profile("prod")
@RestController
@RequestMapping("/internal/seed")
@RequiredArgsConstructor
public class LoadTestSeedController {

	private final LoadTestDataSeeder loadTestDataSeeder;

	/**
	 * 모든 테이블을 비우고 부하테스트 시드 데이터를 다시 주입합니다.
	 * 기존 데이터가 전부 사라지므로 부하테스트 직전에만 호출해야 합니다.
	 */
	@PostMapping
	public CommonResponse<String> seed() {
		log.info("[LoadTestSeedController] seed() 호출 - 시드 데이터 재주입 시작");
		loadTestDataSeeder.seed();
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, "시드 데이터 주입 완료");
	}
}
