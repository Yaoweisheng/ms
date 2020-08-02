package com.yws.config;

import com.yws.utils.MqConstant;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * 队列配置，所有配置@Bean的队列名称，由系统启动时创建队列，并绑定到Exchane上
 */
@Configuration
public class QueueConfiguration {

    //信道配置
    @Bean
    public DirectExchange defaultExchange() {
        return new DirectExchange(MqConstant.DEFAULT_EXCHANGE, true, false);
    }

//    @Bean
//    public DirectExchange deadLetterExchange() {
//        return new DirectExchange(MqConstant.DEAD_LETTER_EXCHANGE, true, false);
//    }

    @Bean
    public Queue queue() {
        Queue queue = new Queue(MqConstant.QUEUE_CANCEL,true);
        return queue;
    }

    @Bean
    public Binding binding() {
        //队列绑定到exchange上，再绑定好路由键
        return BindingBuilder.bind(queue()).to(defaultExchange()).with(MqConstant.QUEUE_CANCEL);
    }
    
    //延迟队列的配置
    //转发队列
    @Bean
    public Queue repeatTradeQueue() {
        Queue queue = new Queue(MqConstant.REPEAT_TRADE_QUEUE,true,false,false);
        return queue;
    }
    //绑定转发队列
    @Bean
    public Binding  drepeatTradeBinding() {
        return BindingBuilder.bind(repeatTradeQueue()).to(defaultExchange()).with(MqConstant.REPEAT_TRADE_QUEUE);
    }

    //死信队列  -- 消息在死信队列上堆积，消息超时时，会把消息转发到转发队列，转发队列根据消息内容再把转发到指定的队列上
    @Bean
    public Queue deadLetterQueue() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-dead-letter-exchange", MqConstant.DEFAULT_EXCHANGE);
        arguments.put("x-dead-letter-routing-key", MqConstant.REPEAT_TRADE_QUEUE);
        Queue queue = new Queue(MqConstant.DEAD_LETTER_QUEUE,true,false,false,arguments);
        return queue;
    }
    //绑定死信队列
    @Bean
    public Binding  deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue()).to(defaultExchange()).with(MqConstant.DEAD_LETTER_QUEUE);
    }
}
