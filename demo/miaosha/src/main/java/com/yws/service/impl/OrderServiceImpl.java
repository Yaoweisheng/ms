package com.yws.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import com.sun.org.apache.xml.internal.resolver.readers.ExtendedXMLCatalogReader;
import com.sun.org.apache.xpath.internal.operations.Or;
import com.yws.dao.OrderDAO;
import com.yws.dao.StockDAO;
import com.yws.dao.UserDAO;
import com.yws.entity.KillInfo;
import com.yws.entity.Order;
import com.yws.entity.Stock;
import com.yws.entity.User;
import com.yws.service.OrderService;
import com.yws.utils.GlobalThreadMap;
import com.yws.utils.StockUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.Argument;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.utils.SerializationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

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
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 线程池
     */
    private ExecutorService executorService;

    public OrderServiceImpl(){
        this.executorService = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors() * 8,
                200,
                3000,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                new ThreadFactory() {
                    private AtomicInteger count = new AtomicInteger(0);
                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "THREAD_" + count.incrementAndGet());
                    }
                });
    }

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
    public void killRedisMq(Integer id, Integer userid) throws JsonProcessingException {
        stockInRedis(id);
        //创建订单
        createOrderByMq(stockDAO.checkStock(id), userid);
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
    public void killMq(Integer id, Integer userid) throws Exception {
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
    public void killMq(Integer id, Integer userid, String md5) throws Exception {
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
    public boolean createOrder(Order order) {
        return orderDAO.createOrder(order) > 0;
    }

    @Override
    public boolean createOrderNX(Order order) {
        //校验库存
        Stock stock = checkStock(order.getSid());
        //扣除库存
        updateSale(stock);
        //生成订单
        return orderDAO.createOrderNX(order) > 0;
    }

    @Override
    public boolean existMsgId(String id) {
        return orderDAO.existMsgid(id) > 0;
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

    @Override
    public Future<Boolean> killInMq(KillInfo killInfo) throws JsonProcessingException {
        String msgJson = objectMapper.writeValueAsString(killInfo);
        Message message = MessageBuilder
                .withBody(msgJson.getBytes())
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(UUID.randomUUID().toString())
                .build();
        rabbitTemplate.convertAndSend("kill", message);
        return executorService.submit(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                GlobalThreadMap.parkThreadMap.put(killInfo.getId(), Thread.currentThread());
                LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(5));
                GlobalThreadMap.parkThreadMap.remove(killInfo.getId());

                if (stringRedisTemplate.hasKey("killIds") && stringRedisTemplate.opsForSet().isMember("killIds", killInfo.getId())) {
                    return true;
                }
                return false;
            }
        });
    }


    //校验库存
    private Stock checkStock(Integer id){
        Stock stock = stockDAO.checkStock(id);
        if(stock.getSale().equals(stock.getCount())){
            throw new RuntimeException("库存不足！！！");
        }
        return stock;
    }

    //redis
    private void stockInRedis(Integer id) {
        String clientId = UUID.randomUUID().toString();
        String freId = StockUtil.STOCK_FREQUENCY + id;
        String freStr = stringRedisTemplate.opsForValue().get(freId);
        int frequency = Integer.parseInt(freStr);
        int randomId = new Random().nextInt(frequency);
        String lockKey = StockUtil.STOCKLOCK + id + ":" + randomId;
        String stockId = StockUtil.STOCK + id;
        String stockcId = stockId + ":" + randomId;
        if(Integer.valueOf(stringRedisTemplate.opsForValue().get(stockId)) <= 0){
            throw new RuntimeException("库存不足！！");
        }
        if(!stringRedisTemplate.opsForValue().setIfAbsent(lockKey, clientId, 1, TimeUnit.SECONDS)) {
            throw new RuntimeException("抢购失败，请重试！！！");
        }
        try{
            Integer num = Integer.valueOf(stringRedisTemplate.opsForValue().get(stockcId));
            if(num <= 0){
                throw new RuntimeException("库存不足！！！");
            }
            stringRedisTemplate.opsForValue().increment(stockcId,-1);
            stringRedisTemplate.opsForValue().increment(stockId, -1);
//            String msgJson = objectMapper.writeValueAsString(id);
//            Message message = MessageBuilder
//                    .withBody(msgJson.getBytes())
//                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
//                    .setMessageId(UUID.randomUUID().toString())
//                    .build();
//            rabbitTemplate.convertAndSend("addStock", message);
        }catch (Exception e){
            throw e;
        }
        finally {
            if(clientId.equals(stringRedisTemplate.opsForValue().get(lockKey))){
                stringRedisTemplate.delete(lockKey);
            }
        }
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
    public void createOrderByMq(Stock stock, Integer userid) throws JsonProcessingException {
        Order order = new Order();
        String msgid = UUID.randomUUID().toString();
        System.out.println(msgid);
        order.setSid(stock.getId()).setName(stock.getName()).setCreateDate(new Date()).setUid(userid).setMsgid(msgid);
//        rabbitTemplate.setMessageConverter(new Jackson2JsonMessageConverter());
//        rabbitTemplate.convertAndSend("order", order);
        String msgJson = objectMapper.writeValueAsString(order);
        Message message = MessageBuilder
                .withBody(msgJson.getBytes())
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(msgid)
                .build();
        rabbitTemplate.convertAndSend("order", message);
    }

    //订单消费者
    @RabbitListener(queuesToDeclare = @Queue("order"))
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
            if(existMsgId(msgId)){
                // 消息即将重复消费，直接确认消费，返回
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                return;
            }
            if(createOrderNX(order)){
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
    @RabbitListener(queuesToDeclare = @Queue("order"))
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
            if(existMsgId(msgId)){
                // 消息即将重复消费，直接确认消费，返回
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                return;
            }
            if(createOrderNX(order)){
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

    //商品消费者
//    @RabbitListener(queuesToDeclare = @Queue("addStock"))
//    public void stockReceive1(Channel channel, Message message) throws IOException {
//        try {
//            String msgId = message.getMessageProperties().getMessageId();
//            Integer id = objectMapper.readValue(message.getBody(), Integer.class);
//
////            if(stringRedisTemplate.hasKey("msgIds") && stringRedisTemplate.opsForSet().isMember("msgIds", msgId)){
////                // 消息即将重复消费，直接确认消费，返回
////                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
////                return;
////            }
//
//            //校验库存
//            Stock stock = checkStock(id);
//            //扣除库存
//            updateSale(stock);
//
//            stringRedisTemplate.opsForSet().add("msgIds", msgId);
//            stringRedisTemplate.expire("msgIds", 2, TimeUnit.MINUTES);
//            System.out.println("message1.stockid= "+stock.getId()+","+"sale="+stock.getSale());
//            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
//        } catch (IOException e) {
//            channel.basicNack(message.getMessageProperties().getDeliveryTag(),false, true);
//            e.printStackTrace();
//        }
//    }


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

            killRedisMq(killInfo.getStockId(), killInfo.getUserId());
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

            killRedisMq(killInfo.getStockId(), killInfo.getUserId());
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

            killRedisMq(killInfo.getStockId(), killInfo.getUserId());
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
