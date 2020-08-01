package com.yws.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.yws.entity.KillInfo;
import com.yws.entity.Order;
import com.yws.entity.Stock;

import java.util.List;
import java.util.concurrent.Future;

public interface OrderService {
    //用来处理秒杀的下单方法，并返回订单id
    int kill(Integer id, Integer userid);

    //redis分布式乐观锁
    void killRedisMq(Integer id, Integer userid) throws JsonProcessingException;

    int killTimeLimit(Integer id, Integer userid);

    //消息队列异步处理订单
    public void killMq(Integer id, Integer userid) throws Exception;

    //用来处理秒杀的下单方法，并返回订单id加入md5接口隐藏的方式，消息队列异步订单处理
    void killMq(Integer id, Integer userid, String md5) throws Exception;

    //用来处理秒杀的下单方法，并返回订单id加入md5接口隐藏的方式
    int kill(Integer id, Integer userid, String md5);



    //创建订单方法
    boolean createOrder(Order order);

    //创建订单方法
    boolean createOrderNX(Order order) throws JsonProcessingException;

    //创建订单方法
    boolean existMsgId(String id);

    //生成md5签名
    String getMd5(Integer id, Integer userid);

    Future<Boolean> killInMq(KillInfo killInfo) throws JsonProcessingException;

    List<Order> getUnpaidOrder(Integer uid, Integer page, Integer per);

    int getUnpaidOrderCount(Integer uid);

    boolean pay(Integer id);

    boolean cancel(Integer id);
}
