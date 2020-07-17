package com.yws.service.impl;

import com.yws.dao.StockDAO;
import com.yws.entity.Stock;
import com.yws.service.StockService;
import com.yws.utils.StockUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@Slf4j
public class StockServiceImpl implements StockService {

    private final int REDIS_SINGLE_CAPACITY = 10;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private StockDAO stockDAO;

    @Override
    public int addStock(String name, Integer count) {
        Stock stock = new Stock();
        stock.setName(name);
        stock.setSale(0);
        stock.setCount(count);
        stock.setVersion(0);
        stockDAO.addStock(stock);
        int stockRedisFrequency = stock.getCount()/REDIS_SINGLE_CAPACITY;
        stringRedisTemplate.opsForValue().set(StockUtil.STOCK+stock.getId(), stock.getCount()+"");
        for(int i = 0; i < stockRedisFrequency; i++){
            String stockId = StockUtil.STOCK+stock.getId()+":"+i;
            stringRedisTemplate.opsForValue().set(stockId, REDIS_SINGLE_CAPACITY+"");
        }
        if(stock.getCount()%REDIS_SINGLE_CAPACITY != 0) {
            stringRedisTemplate.opsForValue().set(StockUtil.STOCK+stock.getId()+":"+stockRedisFrequency, stock.getCount()%REDIS_SINGLE_CAPACITY+"");
            stockRedisFrequency++;
        }
        stringRedisTemplate.opsForValue().set(StockUtil.STOCK_FREQUENCY +stock.getId(), stockRedisFrequency+"");
        return stock.getId();
    }
}
