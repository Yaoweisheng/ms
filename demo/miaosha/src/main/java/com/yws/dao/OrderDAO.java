package com.yws.dao;

import com.yws.entity.Order;

import java.util.Date;
import java.util.List;

public interface OrderDAO {
    //创建订单方法
    int createOrder(Order order);

    int createOrderNX(Order order);

    int existMsgid(String msgid);

    List<Order> getOrder(Integer id, Integer sid, Integer uid, String name, Date startTime, Date endTime, Integer state, Integer limit, Integer offset);

    int getOrderCount(Integer id, Integer sid, Integer uid, String name, Date startTime, Date endTime, Integer state);

    int updateState(Integer id, Integer state, Integer oldState);

    int getState(Integer id);
}
