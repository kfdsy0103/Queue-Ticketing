package ticketing.global.loadTestSeed;

import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class LoadTestDataSeeder {

	private static final int USER_COUNT = 100_000;
	private static final int BATCH_SIZE = 1_000;
	private static final String VENUE_NAME = "부하테스트 아레나";
	private static final String CONCERT_TITLE = "부하테스트 콘서트";

	// 등급별 좌석 수/가격 (총 3000석)
	private static final List<GradeSpec> GRADE_SPECS = List.of(
		new GradeSpec("VIP", 400, 180_000),
		new GradeSpec("R", 800, 150_000),
		new GradeSpec("S", 1800, 120_000)
	);

	private record GradeSpec(String name, int seatCount, int price) {}

	private final JdbcTemplate jdbcTemplate;

	/**
	 * 유저 10만 + 공연장 1개(3천석, 등급 3개) + 콘서트 1개(1회차)를 주입합니다.
	 */
	public void seed() {

		long startMillis = System.currentTimeMillis();
		LocalDateTime now = LocalDateTime.now();

		truncateAllTables();	// PK가 1부터 시작하도록 기존 데이터 제거
		seedUsers(now);	// 유저 주입
		long venueId = seedVenue(now);	// 콘서트장 주입
		Map<String, Long> gradeIdsByName = seedSeatGrades(now);
		seedSeats(venueId, gradeIdsByName, now);	// 좌석 주입
		long concertScheduleId = seedConcertAndSchedule(venueId, gradeIdsByName, now);	// 콘서트 회차 주입

		log.info("[LoadTestDataSeeder] 시드 데이터 주입 완료 - users={}, seats={}, concertScheduleId={} ({}ms)",
			USER_COUNT,
			GRADE_SPECS.stream().mapToInt(GradeSpec::seatCount).sum(),
			concertScheduleId,
			System.currentTimeMillis() - startMillis);
	}

	/**
	 * 현재 스키마의 모든 테이블을 비웁니다.
	 *
	 * DELETE는 AUTO_INCREMENT를 되돌리지 않으므로 TRUNCATE를 씁니다.
	 * FK 제약 때문에 삭제 순서를 따지는 대신 세션 단위로 체크를 끄는데,
	 * 이 설정은 커넥션에 종속되므로 ConnectionCallback으로 하나의 커넥션에서 처리합니다.
	 */
	private void truncateAllTables() {
		jdbcTemplate.execute((ConnectionCallback<Void>)connection -> {
			try (Statement statement = connection.createStatement()) {
				statement.execute("SET FOREIGN_KEY_CHECKS = 0");
				try {
					List<String> tableNames = new ArrayList<>();
					try (ResultSet resultSet = statement.executeQuery(
						"SELECT table_name FROM information_schema.tables "
							+ "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'")) {
						while (resultSet.next()) {
							tableNames.add(resultSet.getString(1));
						}
					}

					for (String tableName : tableNames) {
						statement.execute("TRUNCATE TABLE `" + tableName + "`");
					}
					log.info("[LoadTestDataSeeder] 테이블 {}개 초기화 완료", tableNames.size());
				} finally {
					// 체크를 끈 채로 커넥션이 풀에 반납되면 이후 쓰기가 FK 검증을 건너뛴다
					statement.execute("SET FOREIGN_KEY_CHECKS = 1");
				}
			}
			return null;
		});
	}

	/**
	 * 유저 10만 명을 PK 1부터 순서대로 주입합니다. (k6에서 userId 1..100000으로 바로 사용)
	 */
	private void seedUsers(LocalDateTime now) {
		String sql = "INSERT INTO users (email, name, role, created_at, updated_at) VALUES (?, ?, 'NORMAL', ?, ?)";

		// 1000개씩 한꺼번에 전송
		List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
		for (int i = 1; i <= USER_COUNT; i++) {
			batch.add(new Object[] {"user" + i + "@test.com", "부하유저" + i, now, now});
			if (batch.size() == BATCH_SIZE) {
				jdbcTemplate.batchUpdate(sql, batch);
				batch.clear();
			}
		}
		if (!batch.isEmpty()) {
			jdbcTemplate.batchUpdate(sql, batch);
		}
	}

	private long seedVenue(LocalDateTime now) {
		jdbcTemplate.update("INSERT INTO venues (name, created_at, updated_at) VALUES (?, ?, ?)", VENUE_NAME, now, now);
		return jdbcTemplate.queryForObject("SELECT id FROM venues WHERE name = ?", Long.class, VENUE_NAME);
	}

	private Map<String, Long> seedSeatGrades(LocalDateTime now) {
		Map<String, Long> gradeIdsByName = new HashMap<>();
		for (GradeSpec spec : GRADE_SPECS) {
			jdbcTemplate.update("INSERT INTO seat_grades (name, created_at, updated_at) VALUES (?, ?, ?)",
				spec.name(), now, now);
			gradeIdsByName.put(spec.name(),
				jdbcTemplate.queryForObject("SELECT id FROM seat_grades WHERE name = ?", Long.class, spec.name()));
		}
		return gradeIdsByName;
	}

	/**
	 * 등급별 좌석을 "VIP-1" 형식의 좌석 번호로 주입합니다.
	 */
	private void seedSeats(long venueId, Map<String, Long> gradeIdsByName, LocalDateTime now) {
		String sql = "INSERT INTO seats (venue_id, seat_grade_id, seat_number, created_at, updated_at) VALUES (?, ?, ?, ?, ?)";

		List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
		for (GradeSpec spec : GRADE_SPECS) {
			long gradeId = gradeIdsByName.get(spec.name());
			for (int i = 1; i <= spec.seatCount(); i++) {
				batch.add(new Object[] {venueId, gradeId, spec.name() + "-" + i, now, now});
				if (batch.size() == BATCH_SIZE) {
					jdbcTemplate.batchUpdate(sql, batch);
					batch.clear();
				}
			}
		}
		if (!batch.isEmpty()) {
			jdbcTemplate.batchUpdate(sql, batch);
		}
	}

	/**
	 * 콘서트 1개 + 회차 1개(티켓 오픈 1시간 전, 공연 30일 후)와 등급별 가격을 주입하고,
	 * 공연장의 모든 좌석을 해당 회차의 AVAILABLE ScheduleSeat로 등록합니다. (INSERT...SELECT로 일괄 처리)
	 */
	private long seedConcertAndSchedule(long venueId, Map<String, Long> gradeIdsByName, LocalDateTime now) {
		jdbcTemplate.update("INSERT INTO concerts (venue_id, title, content, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
			venueId, CONCERT_TITLE, "10만 유저 대기열 부하 테스트용 콘서트", now, now);
		long concertId = jdbcTemplate.queryForObject("SELECT id FROM concerts WHERE title = ?", Long.class, CONCERT_TITLE);

		jdbcTemplate.update(
			"INSERT INTO concert_schedules (concert_id, performance_date, ticket_open_at, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
			concertId, LocalDate.now().plusDays(30), now.minusHours(1), now, now);
		long concertScheduleId = jdbcTemplate.queryForObject(
			"SELECT id FROM concert_schedules WHERE concert_id = ?", Long.class, concertId);

		for (GradeSpec spec : GRADE_SPECS) {
			jdbcTemplate.update(
				"INSERT INTO schedule_prices (concert_schedule_id, seat_grade_id, price, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
				concertScheduleId, gradeIdsByName.get(spec.name()), spec.price(), now, now);
		}

		jdbcTemplate.update(
			"INSERT INTO schedule_seats (concert_schedule_id, seat_id, seat_status, created_at, updated_at) "
				+ "SELECT ?, id, 'AVAILABLE', ?, ? FROM seats WHERE venue_id = ?",
			concertScheduleId, now, now, venueId);

		return concertScheduleId;
	}
}
