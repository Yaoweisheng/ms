//package com.yws.mq;
//
//import org.springframework.amqp.rabbit.annotation.Exchange;
//import org.springframework.amqp.rabbit.annotation.Queue;
//import org.springframework.amqp.rabbit.annotation.QueueBinding;
//import org.springframework.amqp.rabbit.annotation.RabbitListener;
//import org.springframework.stereotype.Service;
//
//@Service
//public class AckCustomer {
//    @RabbitListener(bindings = {
//            @QueueBinding(
//                    value = @Queue,//创建临时队列
//                    exchange = @Exchange(value = "ack_exchange", type = "direct"),//指定交换机名称和类型
//                    key = {"ack_key"}
//            )
//    })
//    public void receive1(String message){
//        System.out.println("message1 = " + message);
//    }
//
//    @RabbitListener(bindings = {
//            @QueueBinding(
//                    value = @Queue,//创建临时队列
//                    exchange = @Exchange(value = "ack_exchange", type = "direct"),//指定交换机名称和类型
//                    key = {"ack_key"}
//            )
//    })
//    public void receive2(String message){
//        System.out.println("message2 = " + message);
//    }
//}
