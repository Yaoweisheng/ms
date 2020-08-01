//package com.yws.controller;
//
//import com.yws.mq.AckPublisher;
////import com.yws.mq.TransactionPublisher;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//import java.util.concurrent.atomic.AtomicInteger;
//
//@RestController
//@RequestMapping("mq")
//@Slf4j
//public class MqController {
//    @Autowired
//    private AckPublisher ackPublisher;
//
////    @Autowired
////    private TransactionPublisher transactionPublisher;
//
//    private AtomicInteger atomicInteger = new AtomicInteger(1);
//
//    @GetMapping("judge")
//    public boolean judge(String name) {
//        for (int i = 0; i < 10; i++) {
//            long start = System.currentTimeMillis();
//            ackPublisher.publish(name + atomicInteger.getAndIncrement());
//            ackPublisher.publish(name + atomicInteger.getAndIncrement());
//            ackPublisher.publish(name + atomicInteger.getAndIncrement());
//            long mid = System.currentTimeMillis();
//            System.out.println("ack cost: " + (mid - start));
//
////            transactionPublisher.publish(name + atomicInteger.getAndIncrement());
////            transactionPublisher.publish(name + atomicInteger.getAndIncrement());
////            transactionPublisher.publish(name + atomicInteger.getAndIncrement());
////            System.out.println("transaction cost: " + (System.currentTimeMillis() - mid));
//        }
//        return true;
//    }
//}
