package com.yws.service;

import com.yws.entity.Order;

public interface OrderService {
    //用来处理秒杀的下单方法，并返回订单id
    int kill(Integer id, Integer userid);

    int killTimeLimit(Integer id, Integer userid);

    //消息队列异步处理订单
    public void killMq(Integer id, Integer userid);

    //用来处理秒杀的下单方法，并返回订单id加入md5接口隐藏的方式
    int kill(Integer id, Integer userid, String md5);


    //用来处理秒杀的下单方法，并返回订单id加入md5接口隐藏的方式，消息队列异步订单处理
    void killbyMq(Integer id, Integer userid, String md5);

    //创建订单方法
    boolean createOrder(Order order);

    //生成md5签名
    String getMd5(Integer id, Integer userid);


}
