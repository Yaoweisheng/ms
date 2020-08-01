//package com.yws.mq;
//
//import org.springframework.amqp.core.Message;
//import org.springframework.amqp.rabbit.connection.CorrelationData;
//import org.springframework.amqp.rabbit.core.RabbitTemplate;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import javax.annotation.PostConstruct;
//import java.util.UUID;
//
//@Service
//public class TransactionPublisher implements RabbitTemplate.ReturnCallback {
//
//    @Autowired
//    private RabbitTemplate rabbitTemplate;
//
//    @PostConstruct
//    public void init() {
//        // 将信道设置为事务模式
//        rabbitTemplate.setChannelTransacted(true);
//        rabbitTemplate.setReturnCallback(this);
//    }
//
//    @Override
//    public void returnedMessage(Message message, int replyCode, String replyText, String exchange, String routingKey) {
//        System.out.println("事务 " + message + " 发送失败");
//    }/**
//     * 一般的用法，推送消息
//     *
//     * @param ans
//     * @return
//     */
//    @Transactional(rollbackFor = Exception.class, transactionManager = "rabbitTransactionManager")
//    public String publish(String ans) {
//        String msg = "transaction msg = " + ans;
//        System.out.println("publish: " + msg);
//
//        CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());
//        rabbitTemplate.convertAndSend("tx_exchange", "tx_key", msg, correlationData);
//        return msg;
//    }
//}
