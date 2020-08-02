package com.yws.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.yws.entity.Order;
import com.yws.utils.MqConstant;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

//监听转发队列，有消息时，把消息转发到目标队列
@Component
public class ReceiverDelayMessage {
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RabbitTemplate rabbitTemplate;
	
    @RabbitHandler
    @RabbitListener(queues = MqConstant.REPEAT_TRADE_QUEUE)
    public void process(Channel channel, Message message) throws IOException {
        try {
            //此时，才把消息发送到指定队列，而实现延迟功能
            DLXMessage dlxMessage = objectMapper.readValue(message.getBody(), DLXMessage.class);
            String msgId = UUID.randomUUID().toString();
            Message message0 = MessageBuilder
                    .withBody(dlxMessage.getBody())
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setMessageId(msgId)
                    .build();
            rabbitTemplate.convertAndSend(dlxMessage.getExchange(), dlxMessage.getQueue(), message0);
            // 业务处理成功后调用，消息会被确认消费
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e){
            channel.basicNack(message.getMessageProperties().getDeliveryTag(),false, true);
            e.printStackTrace();
        }
    }

}