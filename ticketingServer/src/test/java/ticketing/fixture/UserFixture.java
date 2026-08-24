package ticketing.fixture;

import ticketing.domain.user.entity.User;

public final class UserFixture {

	private UserFixture() {
	}

	public static User user(Long id) {
		return User.builder()
			.id(id)
			.email("user" + id + "@ticketing.com")
			.name("사용자" + id)
			.role(User.Role.NORMAL)
			.build();
	}
}
