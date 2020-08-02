package com.yws.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.yws.entity.KillInfo;
import com.yws.entity.Order;
import com.yws.service.OrderService;
import com.yws.utils.MqConstant;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Argument;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
@Transactional
public class OrderCustomer {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ObjectMapper objectMapper;


    //订单消费者
    @RabbitListener(queuesToDeclare = @Queue(MqConstant.QUEUE_ORDER))
    public void orderNoRepeatedReceive1(Channel channel, Message message) throws Exception {
        try {
            String msgId = message.getMessageProperties().getMessageId();
            Order order = objectMapper.readValue(message.getBody(), Order.class);
//            Order order = (Order) SerializationUtils.deserialize(message.getBody());
//            if(stringRedisTemplate.hasKey("msgIds") && stringRedisTemplate.opsForSet().isMember("msgIds", msgId)){
//                // 消息即将重复消费，直接确认消费，返回
//                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
//                return;
//            }
            if(orderService.existMsgId(msgId)){
                // 消息即将重复消费，直接确认消费，返回
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                return;
            }
            if(orderService.createOrderNX(order)){
//                stringRedisTemplate.opsForSet().add("msgIds", msgId);
//                stringRedisTemplate.expire("msgIds", 2, TimeUnit.MINUTES);
//                System.out.println("message1.order= "+order.getId());
                // 业务处理成功后调用，消息会被确认消费
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            } else{
                // 业务处理失败后调用
                channel.basicNack(message.getMessageProperties().getDeliveryTag(),false, true);
//                channel.basicReject(message.getMessageProperties().getDeliveryTag(), true);
            }
        } catch (Exception e) {
            channel.basicNack(message.getMessageProperties().getDeliveryTag(),false, true);
            e.printStackTrace();
            throw new RuntimeException();//抛出运行时异常保证@Transactional执行
        }
    }
    //订单消费者
    @RabbitListener(queuesToDeclare = @Queue(MqConstant.QUEUE_ORDER))
    public void orderNoRepeatedReceive2(Channel channel, Message message) throws Exception {
//        Message message = correlationData.getReturnedMessage();
        try {
            String msgId = message.getMessageProperties().getMessageId();
            Order order = objectMapper.readValue(message.getBody(), Order.class);
//            Order order = (Order) SerializationUtils.deserialize(message.getBody());
//            if(stringRedisTemplate.hasKey("msgIds") && stringRedisTemplate.opsForSet().isMember("msgIds", msgId)){
//                // 消息即将重复消费，直接确认消费，返回
//                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
//                return;
//            }
            if(orderService.existMsgId(msgId)){
                // 消息即将重复消费，直接确认消费，返回
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                return;
            }
            if(orderService.createOrderNX(order)){
//                stringRedisTemplate.opsForSet().add("msgIds", msgId);
//                stringRedisTemplate.expire("msgIds", 2, TimeUnit.MINUTES);
//                System.out.println("message2.order= "+order.getId());
                // 业务处理成功后调用，消息会被确认消费
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            } else{
                // 业务处理失败后调用
                channel.basicNack(message.getMessageProperties().getDeliveryTag(),false, true);
//                channel.basicReject(message.getMessageProperties().getDeliveryTag(), true);
            }
        } catch (Exception e) {
            channel.basicNack(message.getMessageProperties().getDeliveryTag(),false, true);
            e.printStackTrace();
            throw new RuntimeException();//抛出运行时异常保证@Transactional执行
        }
    }


    //订单取消的消费者
    @RabbitListener(queuesToDeclare = @Queue(MqConstant.QUEUE_CANCEL))
    public void orderCancelReceive1(Channel channel, Message message) throws Exception {
        try {
            Order order = objectMapper.readValue(message.getBody(), Order.class);
            if(orderService.getState(order.getId()) != 0){
                // 消息即将重复消费，直接确认消费，返回
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                return;
            }
            orderService.cancel(order);
            // 业务处理成功后调用，消息会被确认消费
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e) {
            channel.basicNack(message.getMessageProperties().getDeliveryTag(),false, true);
            e.printStackTrace();
            throw new RuntimeException();//抛出运行时异常保证@Transactional执行
        }
    }

    //秒杀消费者
    @RabbitListener(queuesToDeclare = @Queue(name = "kill", durable = "true", arguments = {@Argument(name = "x-max-length", value = "100", type = "java.lang.Integer")}))
    public void killReceive1(Channel channel, Message message) throws IOException {
        try {
            String msgId = message.getMessageProperties().getMessageId();
            KillInfo killInfo = objectMapper.readValue(message.getBody(), KillInfo.class);

            if(stringRedisTemplate.hasKey("msgIds") && stringRedisTemplate.opsForSet().isMember("msgIds", msgId)){
                // 消息即将重复消费，直接确认消费，返回
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                return;
            }
//            KillInfo killInfo = (KillInfo) SerializationUtils.deserialize(message.getBody());

            orderService.killRedisMq(killInfo.getStockId(), killInfo.getUserId());
            stringRedisTemplate.opsForSet().add("killIds", killInfo.getId());
            stringRedisTemplate.expire("killIds", 2, TimeUnit.MINUTES);

            stringRedisTemplate.opsForSet().add("msgIds", msgId);
            stringRedisTemplate.expire("msgIds", 2, TimeUnit.MINUTES);

            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (IOException e) {
            channel.basicNack(message.getMessageProperties().getDeliveryTag(),false, false);
            e.printStackTrace();
        }
    }


    //秒杀消费者
    @RabbitListener(queuesToDeclare = @Queue(name = "kill", durable = "true", arguments = {@Argument(name = "x-max-length", value = "100", type = "java.lang.Integer")}))
    public void killReceive2(Channel channel, Message message) throws IOException {
        try {
            String msgId = message.getMessageProperties().getMessageId();
            KillInfo killInfo = objectMapper.readValue(message.getBody(), KillInfo.class);

            if(stringRedisTemplate.hasKey("msgIds") && stringRedisTemplate.opsForSet().isMember("msgIds", msgId)){
                // 消息即将重复消费，直接确认消费，返回
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                return;
            }
//            KillInfo killInfo = (KillInfo) SerializationUtils.deserialize(message.getBody());

            orderService.killRedisMq(killInfo.getStockId(), killInfo.getUserId());
            stringRedisTemplate.opsForSet().add("killIds", killInfo.getId());
            stringRedisTemplate.expire("killIds", 2, TimeUnit.MINUTES);

            stringRedisTemplate.opsForSet().add("msgIds", msgId);
            stringRedisTemplate.expire("msgIds", 2, TimeUnit.MINUTES);

            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (IOException e) {
            channel.basicNack(message.getMessageProperties().getDeliveryTag(),false, false);
            e.printStackTrace();
        }
    }


    //秒杀消费者
    @RabbitListener(queuesToDeclare = @Queue(name = "kill", durable = "true", arguments = {@Argument(name = "x-max-length", value = "100", type = "java.lang.Integer")}))
    public void killReceive3(Channel channel, Message message) throws IOException {
        try {
            String msgId = message.getMessageProperties().getMessageId();
            KillInfo killInfo = objectMapper.readValue(message.getBody(), KillInfo.class);

            if(stringRedisTemplate.hasKey("msgIds") && stringRedisTemplate.opsForSet().isMember("msgIds", msgId)){
                // 消息即将重复消费，直接确认消费，返回
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                return;
            }
//            KillInfo killInfo = (KillInfo) SerializationUtils.deserialize(message.getBody());

            orderService.killRedisMq(killInfo.getStockId(), killInfo.getUserId());
            stringRedisTemplate.opsForSet().add("killIds", killInfo.getId());
            stringRedisTemplate.expire("killIds", 2, TimeUnit.MINUTES);

            stringRedisTemplate.opsForSet().add("msgIds", msgId);
            stringRedisTemplate.expire("msgIds", 2, TimeUnit.MINUTES);

            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (IOException e) {
            channel.basicNack(message.getMessageProperties().getDeliveryTag(),false, false);
            e.printStackTrace();
        }
    }
}
