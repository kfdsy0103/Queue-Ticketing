package ticketing.domain.order.order.service;

import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.order.order.dto.CreateDTO;
import ticketing.domain.order.order.dto.GetAllDTO;
import ticketing.domain.order.order.dto.GetByIdDTO;
import ticketing.domain.order.order.dto.UpdateDTO;
import ticketing.domain.order.order.entity.Order;
import ticketing.domain.order.order.exception.OrderErrorCode;
import ticketing.domain.order.order.repository.OrderRepository;
import ticketing.domain.user.entity.User;
import ticketing.domain.user.exception.UserErrorCode;
import ticketing.domain.user.repository.UserRepository;
import ticketing.global.apiPayload.exception.GeneralException;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

	private final OrderRepository orderRepository;
	private final UserRepository userRepository;

	public CreateDTO.Response create(CreateDTO.Command command) {
		Order order = Order.builder()
			.user(findUser(command.getUserId()))
			.orderStatus(command.getOrderStatus())
			.totalPrice(command.getTotalPrice())
			.build();

		return CreateDTO.Response.from(orderRepository.save(order));
	}

	public GetByIdDTO.Response getById(Long id) {
		return GetByIdDTO.Response.from(findOrder(id));
	}

	public List<GetAllDTO.Response> getAll() {
		return orderRepository.findAll().stream()
			.map(GetAllDTO.Response::from)
			.toList();
	}

	public UpdateDTO.Response update(Long id, UpdateDTO.Command command) {
		Order order = findOrder(id);
		order.update(findUser(command.getUserId()), command.getOrderStatus(), command.getTotalPrice());

		return UpdateDTO.Response.from(orderRepository.save(order));
	}

	public void delete(Long id) {
		orderRepository.delete(findOrder(id));
	}

	private Order findOrder(Long id) {
		return orderRepository.findById(id)
			.orElseThrow(() -> new GeneralException(OrderErrorCode.ORDER_NOT_FOUND));
	}

	private User findUser(Long id) {
		return userRepository.findById(id)
			.orElseThrow(() -> new GeneralException(UserErrorCode.USER_NOT_FOUND));
	}
}
