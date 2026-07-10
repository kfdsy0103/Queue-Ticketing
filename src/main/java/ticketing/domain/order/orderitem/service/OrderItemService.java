package ticketing.domain.order.orderitem.service;

import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.concert.scheduleseat.entity.ScheduleSeat;
import ticketing.domain.concert.scheduleseat.exception.ScheduleSeatErrorCode;
import ticketing.domain.concert.scheduleseat.repository.ScheduleSeatRepository;
import ticketing.domain.order.order.entity.Order;
import ticketing.domain.order.order.exception.OrderErrorCode;
import ticketing.domain.order.order.repository.OrderRepository;
import ticketing.domain.order.orderitem.dto.CreateDTO;
import ticketing.domain.order.orderitem.dto.GetAllDTO;
import ticketing.domain.order.orderitem.dto.GetByIdDTO;
import ticketing.domain.order.orderitem.dto.UpdateDTO;
import ticketing.domain.order.orderitem.entity.OrderItem;
import ticketing.domain.order.orderitem.exception.OrderItemErrorCode;
import ticketing.domain.order.orderitem.repository.OrderItemRepository;
import ticketing.global.apiPayload.exception.GeneralException;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderItemService {

	private final OrderItemRepository orderItemRepository;
	private final OrderRepository orderRepository;
	private final ScheduleSeatRepository scheduleSeatRepository;

	public CreateDTO.Response create(CreateDTO.Command command) {
		OrderItem orderItem = OrderItem.builder()
			.order(findOrder(command.getOrderId()))
			.scheduleSeat(findScheduleSeat(command.getScheduleSeatId()))
			.price(command.getPrice())
			.build();

		return CreateDTO.Response.from(orderItemRepository.save(orderItem));
	}

	public GetByIdDTO.Response getById(Long id) {
		return GetByIdDTO.Response.from(findOrderItem(id));
	}

	public List<GetAllDTO.Response> getAll() {
		return orderItemRepository.findAll().stream()
			.map(GetAllDTO.Response::from)
			.toList();
	}

	public UpdateDTO.Response update(Long id, UpdateDTO.Command command) {
		OrderItem orderItem = findOrderItem(id);
		orderItem.update(findOrder(command.getOrderId()), findScheduleSeat(command.getScheduleSeatId()), command.getPrice());

		return UpdateDTO.Response.from(orderItemRepository.save(orderItem));
	}

	public void delete(Long id) {
		orderItemRepository.delete(findOrderItem(id));
	}

	private OrderItem findOrderItem(Long id) {
		return orderItemRepository.findById(id)
			.orElseThrow(() -> new GeneralException(OrderItemErrorCode.ORDER_ITEM_NOT_FOUND));
	}

	private Order findOrder(Long id) {
		return orderRepository.findById(id)
			.orElseThrow(() -> new GeneralException(OrderErrorCode.ORDER_NOT_FOUND));
	}

	private ScheduleSeat findScheduleSeat(Long id) {
		return scheduleSeatRepository.findById(id)
			.orElseThrow(() -> new GeneralException(ScheduleSeatErrorCode.SCHEDULE_SEAT_NOT_FOUND));
	}
}
