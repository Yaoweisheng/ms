package com.yws.service.impl;

import com.rabbitmq.client.Channel;
import com.sun.org.apache.xpath.internal.operations.Or;
import com.yws.dao.OrderDAO;
import com.yws.dao.StockDAO;
import com.yws.dao.UserDAO;
import com.yws.entity.Order;
import com.yws.entity.Stock;
import com.yws.entity.User;
import com.yws.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.utils.SerializationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Service
@Transactional
@Slf4j
public class OrderServiceImpl implements OrderService {

    //注入rabbitTemplate
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private StockDAO stockDAO;

    @Autowired
    private OrderDAO orderDAO;

    @Autowired
    private UserDAO userDAO;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public int kill(Integer id, Integer userid) {
        //校验redis中秒杀商品是否超时
//        if (!stringRedisTemplate.hasKey("kill:"+id)) {
//            throw new RuntimeException("当前商品的抢购活动已经结束啦");
//        }
        //校验库存
        Stock stock = checkStock(id);
        //扣除库存
        updateSale(stock);
        //创建订单
        return createOrder(stock, userid);
    }

    @Override
    public int killTimeLimit(Integer id, Integer userid) {
        //校验redis中秒杀商品是否超时
        if (!stringRedisTemplate.hasKey("kill:"+id)) {
            throw new RuntimeException("当前商品的抢购活动已经结束啦");
        }
        //校验库存
        Stock stock = checkStock(id);
        //扣除库存
        updateSale(stock);
        //创建订单
        return createOrder(stock, userid);
    }

    @Override
    public void killMq(Integer id, Integer userid) {
        //校验redis中秒杀商品是否超时
//        if (!stringRedisTemplate.hasKey("kill:"+id)) {
//            throw new RuntimeException("当前商品的抢购活动已经结束啦");
//        }
        //校验库存
        Stock stock = checkStock(id);
        //扣除库存
        updateSale(stock);
        //创建订单
        createOrderByMq(stock, userid);
    }

    @Override
    public int kill(Integer id, Integer userid, String md5) {
        //校验redis中秒杀商品是否超时
//        if (!stringRedisTemplate.hasKey("kill:"+id)) {
//            throw new RuntimeException("当前商品的抢购活动已经结束啦");
//        }
        //验证签名
        String hashKey = "KEY_"+userid+"_"+id;
        String s = stringRedisTemplate.opsForValue().get(hashKey);
        if(s==null){
            throw new RuntimeException("没有携带验证签名，请求不合法");
        }
        if (!s.equals(md5)) {
            throw new RuntimeException("当前请求数据不合法，请稍后再试");
        }

        //校验库存
        Stock stock = checkStock(id);
        //扣除库存
        updateSale(stock);
        //创建订单
        return createOrder(stock, userid);
    }

    @Override
    public void killbyMq(Integer id, Integer userid, String md5) {
        //校验redis中秒杀商品是否超时
//        if (!stringRedisTemplate.hasKey("kill:"+id)) {
//            throw new RuntimeException("当前商品的抢购活动已经结束啦");
//        }
        //验证签名
        String hashKey = "KEY_"+userid+"_"+id;
        String s = stringRedisTemplate.opsForValue().get(hashKey);
        if(s==null){
            throw new RuntimeException("没有携带验证签名，请求不合法");
        }
        if (!s.equals(md5)) {
            throw new RuntimeException("当前请求数据不合法，请稍后再试");
        }

        //校验库存
        Stock stock = checkStock(id);
        //扣除库存
        updateSale(stock);
        //创建订单
        createOrderByMq(stock, userid);
    }

    @Override
    public boolean createOrder(Order order) {
        return orderDAO.createOrder(order) > 0 ? true : false;
    }

    @Override
    public String getMd5(Integer id, Integer userid) {
        //检验用户的合法性
        User user = userDAO.findById(userid);
        if(user==null)throw new RuntimeException("用户信息不存在!");
        log.info("用户信息:[{}]",user.toString());
        //检验商品的合法行
        Stock stock = stockDAO.checkStock(id);
        if(stock==null) throw new RuntimeException("商品信息不合法!");
        log.info("商品信息:[{}]",stock.toString());
        //生成hashkey
        String hashKey = "KEY_"+userid+"_"+id;
        //生成md5//这里!QS#是一个盐 随机生成
        String key = DigestUtils.md5DigestAsHex((userid+id+"!Q*jS#").getBytes());
        stringRedisTemplate.opsForValue().set(hashKey, key, 120, TimeUnit.SECONDS);
        log.info("Redis写入：[{}] [{}]", hashKey, key);
        return key;
    }

    //校验库存
    private Stock checkStock(Integer id){
        Stock stock = stockDAO.checkStock(id);
        if(stock.getSale().equals(stock.getCount())){
            throw new RuntimeException("库存不足！！！");
        }
        return stock;
    }

    //扣除库存
    private void updateSale(Stock stock){
        //在sql层面完成销量的+1 和版本号的+ 并且根据商品id和版本号同时查询更新的商品
//        stock.setSale(stock.getSale()+1);
        int updateSale = stockDAO.updateSale(stock);
        if(updateSale == 0){
            throw new RuntimeException("抢购失败，请重试！！！");
//            kill(stock.getId());
        }
    }

    //创建订单
    public Integer createOrder(Stock stock, Integer userid){
        Order order = new Order();
        order.setSid(stock.getId()).setName(stock.getName()).setCreateDate(new Date()).setUid(userid);
        orderDAO.createOrder(order);
        return order.getId();
    }

    //消息队列异步创建订单
    public void createOrderByMq(Stock stock, Integer userid){
        Order order = new Order();
        order.setSid(stock.getId()).setName(stock.getName()).setCreateDate(new Date()).setUid(userid);
        rabbitTemplate.convertAndSend("order", order);
    }

    //一个消费者
    @RabbitListener(queuesToDeclare = @Queue("order"))
    public void receive1(Channel channel, Message message){
        try {
            Order order = (Order) SerializationUtils.deserialize(message.getBody());
            if(createOrder(order)){
                System.out.println("message1.order= "+order.getId());
                // 业务处理成功后调用，消息会被确认消费
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            } else{
                // 业务处理失败后调用
//                channel.basicNack(message.getMessageProperties().getDeliveryTag(),false, true);
                channel.basicReject(message.getMessageProperties().getDeliveryTag(), true);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //一个消费者
    @RabbitListener(queuesToDeclare = @Queue("order"))
    public void receive2(Channel channel, Message message){
        try {
            Order order = (Order) SerializationUtils.deserialize(message.getBody());
            if(createOrder(order)){
                System.out.println("message2.order= "+order.getId());
                // 业务处理成功后调用，消息会被确认消费
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            } else{
                // 业务处理失败后调用
//                channel.basicNack(message.getMessageProperties().getDeliveryTag(),false, true);
                channel.basicReject(message.getMessageProperties().getDeliveryTag(), true);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
