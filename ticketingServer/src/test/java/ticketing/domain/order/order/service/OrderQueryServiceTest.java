package ticketing.domain.order.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ticketing.domain.order.order.dto.FindDTO;
import ticketing.domain.order.order.entity.Order;
import ticketing.domain.order.order.repository.OrderRepository;
import ticketing.fixture.OrderFixture;
import ticketing.fixture.UserFixture;
import ticketing.global.apiPayload.code.GeneralErrorCode;
import ticketing.global.apiPayload.exception.GeneralException;

/**
 * OrderQueryService의 주문 단건 조회 소유자 검증을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class OrderQueryServiceTest {

	@InjectMocks
	private OrderQueryService orderQueryService;

	@Mock
	private OrderRepository orderRepository;

	@Test
	void 주문_소유자가_아니면_FORBIDDEN_예외가_발생한다() {
		// given
		Order order = OrderFixture.pendingOrder(1L, UserFixture.user(1L), 10_000);
		given(orderRepository.findById(1L)).willReturn(Optional.of(order));

		// when
		Throwable thrown = catchThrowable(() -> orderQueryService.find(OrderFixture.findCommand(1L, 99L)));

		// then
		assertThat(thrown).isInstanceOf(GeneralException.class);
		assertThat(((GeneralException)thrown).getCode()).isEqualTo(GeneralErrorCode.FORBIDDEN);
	}

	@Test
	void 소유자가_조회하면_주문_정보가_반환된다() {
		// given
		LocalDateTime createdAt = LocalDateTime.of(2026, 8, 24, 12, 0);
		Order order = OrderFixture.pendingOrderCreatedAt(1L, UserFixture.user(1L), 10_000, createdAt);
		given(orderRepository.findById(1L)).willReturn(Optional.of(order));

		// when
		FindDTO.Result result = orderQueryService.find(OrderFixture.findCommand(1L, 1L));

		// then
		assertThat(result.getOrderId()).isEqualTo(1L);
		assertThat(result.getOrderStatus()).isEqualTo(Order.OrderStatus.PENDING);
		assertThat(result.getTotalPrice()).isEqualTo(10_000);
		assertThat(result.getCreatedAt()).isEqualTo(createdAt);
	}
}
