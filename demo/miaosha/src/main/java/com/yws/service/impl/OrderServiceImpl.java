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
import com.yws.utils.MqConstant;
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

import javax.annotation.PostConstruct;
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
    public boolean createOrderNX(Order order) throws JsonProcessingException {
        //校验库存
        Stock stock = checkStock(order.getSid());
        //扣除库存
        int updateSale = stockDAO.updateSale(stock);
        //乐观锁更新失败，自旋尝试多次更新
        while(updateSale == 0){
            stock = checkStock(order.getSid());
            updateSale = stockDAO.updateSale(stock);
        }
        //生成订单
        if(orderDAO.createOrderNX(order) > 0){
            //定时实现30分钟未付款取消订单
            delayCencel(order);
            return true;
        }
        return false;
    }

    //TODO 定时实现30分钟未付款取消订单
    private void delayCencel(Order order) {
        //cancel(order.getId());
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
        order.setSid(stock.getId()).setName(stock.getName()).setCreateTime(new Date()).setUid(userid);
        orderDAO.createOrder(order);
        return order.getId();
    }

    //消息队列异步创建订单
    public void createOrderByMq(Stock stock, Integer userid) throws JsonProcessingException {
        Order order = new Order();
        String msgid = UUID.randomUUID().toString();
        System.out.println(msgid);
        order.setSid(stock.getId()).setName(stock.getName()).setCreateTime(new Date()).setUid(userid).setMsgid(msgid);
//        rabbitTemplate.setMessageConverter(new Jackson2JsonMessageConverter());
//        rabbitTemplate.convertAndSend("order", order);
        String msgJson = objectMapper.writeValueAsString(order);
        Message message = MessageBuilder
                .withBody(msgJson.getBytes())
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(msgid)
                .build();
        rabbitTemplate.convertAndSend(MqConstant.QUEUE_ORDER, message);
    }


    @Override
    public List<Order> getUnpaidOrder(Integer uid, Integer page, Integer per){
        return orderDAO.getOrder(null, null, uid, null, null, null, 0, (page-1)*per, per);
    }

    @Override
    public int getUnpaidOrderCount(Integer uid){
        return orderDAO.getOrderCount(null, null, uid, null, null, null, 0);
    }

    @Override
    public boolean pay(Integer id) {
        return orderDAO.updateState(id, 1, 0) > 0;
    }

    @Override
    public boolean cancel(Integer id) {
        if(orderDAO.updateState(id, 2, 0) > 0){
            Stock stock = stockDAO.checkStock(id);
            int restoreSale = stockDAO.restoreSale(stock);
            while(restoreSale == 0){
                restoreSale = stockDAO.restoreSale(stock);
            }
            return true;
        }
        return false;
    }
}
