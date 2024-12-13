//School of Informatics Xiamen University, GPL-3.0 license

package cn.edu.xmu.javaee.order.service;

import cn.edu.xmu.javaee.core.model.dto.UserDto;
import cn.edu.xmu.javaee.core.util.SnowFlakeIdWorker;
import cn.edu.xmu.javaee.order.dao.OrderDao;
import cn.edu.xmu.javaee.order.dao.bo.Order;
import cn.edu.xmu.javaee.order.dao.bo.OrderItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class OrderService {

    private OrderDao orderDao;

    private SnowFlakeIdWorker snowFlakeIdWorker;

    @Autowired
    public OrderService(OrderDao orderDao, SnowFlakeIdWorker snowFlakeIdWorker) {
        this.orderDao = orderDao;
        this.snowFlakeIdWorker = snowFlakeIdWorker;
    }

    public Order createOrder(Order order, UserDto customer) {
        order.setOrderSn(String.format("O%d", this.snowFlakeIdWorker.nextId()));
        for (OrderItem item : order.getOrderItems()){
            item.setId(this.snowFlakeIdWorker.nextId());
        }
        return this.orderDao.insert(order);
    }

    public Order findOrder(String id){
        return this.orderDao.findById(id);
    }
}
