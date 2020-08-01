package com.yws.controller;

import com.google.common.util.concurrent.RateLimiter;
import com.yws.entity.KillInfo;
import com.yws.entity.Order;
import com.yws.entity.Stock;
import com.yws.service.OrderService;
import com.yws.service.StockService;
import com.yws.service.UserService;
import com.yws.utils.ObjectResult;
import com.yws.utils.PagingObject;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("stock")
@Slf4j
public class StockController {

    //注入rabbitTemplate
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private StockService stockService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private UserService userService;

    //创建令牌桶实例
    private RateLimiter rateLimiter = RateLimiter.create(40);

    //生成md5值的方法
    @GetMapping("md5")
    public String getMd5(Integer id, Integer userid) {
        String md5;
        try {
            md5 = orderService.getMd5(id, userid);
        }catch (Exception e){
            e.printStackTrace();
            return "获取md5失败: "+e.getMessage();
        }
        return "获取md5信息为: "+md5;
    }

    //添加stock
//    @PostMapping("addStock")
//    public String addStock() {
//        return null;
//    }

//    @RequestMapping("workmq")
//    public String workmq(String str){
//        String result = "work 模型: "+str;
//        rabbitTemplate.convertAndSend("order", result);
//        return result;
//    }

    //开发秒杀方法,使用乐观锁防止超卖
    @GetMapping("addStock")
    public String addStock(String name, Integer count){
        try{
            int stockId = stockService.addStock(name, count);
            return "添加商品成功,商品id为"+stockId;
        } catch (Exception e){
            e.printStackTrace();
            return e.getMessage();
        }
    }


    //开发秒杀方法,使用乐观锁防止超卖
    @GetMapping("getStock")
    public List<Stock> getStock(String name){
        try{
            return stockService.getStock(name);
        } catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }

    @GetMapping("transactionTest")
    public int transactionTest(){
        try {
            return stockService.transactionTest();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }

    //开发秒杀方法,使用乐观锁防止超卖
    @GetMapping("kill")
    public String kill(Integer id, Integer userid){
        System.out.println("用户id = " + userid +" 秒杀的商品id = " + id);
        try{
            //根据秒杀商品id 去调用秒杀业务
            int orderId = orderService.kill(id, userid);
            return "秒杀成功，订单id为："+String.valueOf(orderId);
        } catch (Exception e){
            e.printStackTrace();
            return e.getMessage();
        }
    }

    //开发秒杀方法,使用乐观锁防止超卖,消息队列异步处理订单
    @GetMapping("killMq")
    public String killMq(Integer id, Integer userid){
        System.out.println("用户id = " + userid +" 秒杀的商品id = " + id);
        try{
            //根据秒杀商品id 去调用秒杀业务
            orderService.killMq(id, userid);
            return "秒杀成功！！！";
        } catch (Exception e){
            e.printStackTrace();
            return e.getMessage();
        }
    }

    //开发秒杀方法,使用乐观锁防止超卖,消息队列异步处理订单
    @GetMapping("killRedisMq")
    public String killRedisMq(Integer id, Integer userid){
        System.out.println("用户id = " + userid +" 秒杀的商品id = " + id);
        try{
            //根据秒杀商品id 去调用秒杀业务
            orderService.killRedisMq(id, userid);
            return "秒杀成功！！！";
        } catch (Exception e){
            e.printStackTrace();
            return e.getMessage();
        }
    }

    //开发秒杀方法,乐观锁防止超卖+令牌桶算法限流
    @GetMapping("killtoken")
    public String killtoken(Integer id, Integer userid){
        System.out.println("秒杀是商品的id = " + id);
        //加入令牌桶的限流措施
        if(!rateLimiter.tryAcquire(2, TimeUnit.SECONDS)){
            log.info("抢购失败，当前秒杀活动过于火爆，请重试！");
            return "抢购失败，当前秒杀活动过于火爆，请重试！";
        }
        try{
            //根据秒杀商品id 去调用秒杀业务
            int orderId = orderService.kill(id, userid);
            return "秒杀成功，订单id为："+String.valueOf(orderId);
        } catch (Exception e){
            e.printStackTrace();
            return e.getMessage();
        }
    }

    //开发秒杀方法,乐观锁防止超卖+消息队列限流
    @GetMapping("killLimitByMq")
    public String killLimitByMq(Integer id, Integer userid){
        System.out.println("秒杀是商品的id = " + id);
        try{
            //秒杀信息放入消息队列中
            KillInfo killInfo = new KillInfo(UUID.randomUUID().toString(), id, userid);
            Future<Boolean> result = orderService.killInMq(killInfo);
            return result.get(10, TimeUnit.SECONDS) ? "秒杀成功!!!":"抢购失败，当前秒杀活动过于火爆，请重试！";
        } catch (Exception e){
            e.printStackTrace();
            return "抢购失败，当前秒杀活动过于火爆，请重试！";
        }
    }

    //开发秒杀方法,乐观锁防止超卖+令牌桶算法限流+消息队列异步处理订单
    @GetMapping("killtokenMq")
    public String killtokenMq(Integer id, Integer userid){
        System.out.println("秒杀是商品的id = " + id);
        //加入令牌桶的限流措施
        if(!rateLimiter.tryAcquire(2, TimeUnit.SECONDS)){
            log.info("抢购失败，当前秒杀活动过于火爆，请重试！");
            return "抢购失败，当前秒杀活动过于火爆，请重试！";
        }
        try{
            //根据秒杀商品id 去调用秒杀业务
            orderService.killMq(id, userid);
            return "秒杀成功!!!";
        } catch (Exception e){
            e.printStackTrace();
            return e.getMessage();
        }
    }


    //开发秒杀方法,乐观锁防止超卖+令牌桶算法限流+md5签名（hash接口隐藏）
    @GetMapping("killtokenmd5")
    public String killtokenmd5(Integer id, Integer userid, String md5){
        System.out.println("秒杀是商品的id = " + id);
        //加入令牌桶的限流措施
        if(!rateLimiter.tryAcquire(2, TimeUnit.SECONDS)){
            log.info("抢购失败，当前秒杀活动过于火爆，请重试！");
            return "抢购失败，当前秒杀活动过于火爆，请重试！";
        }
        try{
            //根据秒杀商品id 去调用秒杀业务
            int orderId = orderService.kill(id, userid, md5);
            return "秒杀成功，订单id为："+String.valueOf(orderId);
        } catch (Exception e){
            e.printStackTrace();
            return e.getMessage();
        }
    }

    //开发秒杀方法,乐观锁防止超卖+令牌桶算法限流+md5签名（hash接口隐藏）+单用户访问频率限制
    @GetMapping("killtokenmd5limit")
    public String killtokenmd5limit(Integer id, Integer userid, String md5){
        System.out.println("秒杀是商品的id = " + id);
        //加入令牌桶的限流措施
        if(!rateLimiter.tryAcquire(2, TimeUnit.SECONDS)){
            log.info("抢购失败，当前秒杀活动过于火爆，请重试！");
            return "抢购失败，当前秒杀活动过于火爆，请重试！";
        }
        try{
            //单用户调用接口的频率限制
            int count = userService.saveUserCount(userid);
            log.info("用户截至该次的访问次数为: [{}]", count);
            //进行调用次数判断
            boolean isBanned = userService.getUserCount(userid);
            if (isBanned) {
                log.info("购买失败,超过频率限制!");
                return "购买失败，超过频率限制!";
            }
            //根据秒杀商品id 去调用秒杀业务
            int orderId = orderService.kill(id, userid, md5);
            return "秒杀成功，订单id为："+String.valueOf(orderId);
        } catch (Exception e){
            e.printStackTrace();
            return e.getMessage();
        }
    }


    //开发秒杀方法,乐观锁防止超卖+令牌桶算法限流+md5签名（hash接口隐藏）+单用户访问频率限制
    @GetMapping("killtokenmd5limitMq")
    public String killtokenmd5limitMq(Integer id, Integer userid, String md5){
        System.out.println("秒杀是商品的id = " + id);
        //加入令牌桶的限流措施
        if(!rateLimiter.tryAcquire(2, TimeUnit.SECONDS)){
            log.info("抢购失败，当前秒杀活动过于火爆，请重试！");
            return "抢购失败，当前秒杀活动过于火爆，请重试！";
        }
        try{
            //单用户调用接口的频率限制
            int count = userService.saveUserCount(userid);
            log.info("用户截至该次的访问次数为: [{}]", count);
            //进行调用次数判断
            boolean isBanned = userService.getUserCount(userid);
            if (isBanned) {
                log.info("购买失败,超过频率限制!");
                return "购买失败，超过频率限制!";
            }
            //根据秒杀商品id 去调用秒杀业务
            orderService.killMq(id, userid, md5);
            return "秒杀成功！！！";
        } catch (Exception e){
            e.printStackTrace();
            return e.getMessage();
        }
    }

    @GetMapping("getUnpaidOrder")
    public ObjectResult<PagingObject> getUnpaidOrder(Integer uid, Integer page, Integer per){
        List<Order> unpaidOrder = orderService.getUnpaidOrder(uid, page, per);
        int unpaidOrderCount = orderService.getUnpaidOrderCount(uid);
        return ObjectResult.success(new PagingObject<>(unpaidOrder, unpaidOrderCount));
    }

    @GetMapping("pay")
    public ObjectResult<Order> pay(Integer id){
        if(orderService.pay(id)){
            return ObjectResult.success("付款成功！");
        }
        return ObjectResult.error("付款失败！");
    }

    @GetMapping("cancel")
    public ObjectResult<Order> cancel(Integer id){
        if(orderService.cancel(id)){
            return ObjectResult.success("订单已取消");
        }
        return ObjectResult.error("订单取消失败！");
    }
//    @GetMapping("sale")
//    public String sale(Integer id){
//        //1、没有获取到token请求一直直到获取到token令牌
////        log.info("等待的时间："+rateLimiter.acquire());
//        //2、设置一个等待时间，如果在等待的时间内获取到了token令牌，则处理业务；如果在等待时间内没有获取到响应token则抛弃请求
//        if (!rateLimiter.tryAcquire(2, TimeUnit.SECONDS)) {
//            System.out.println("当前请求被限流，直接抛弃，无法调用后续秒杀逻辑");
//            return "抢购失败";
//        }
//        System.out.println("处理业务..............................");
//        return "抢购成功";
//    }
}
