package com.yws.dao;

import com.yws.entity.Order;

public interface OrderDAO {
    //创建订单方法
    int createOrder(Order order);

    int createOrderNX(Order order);

    int existMsgid(String msgid);
}
