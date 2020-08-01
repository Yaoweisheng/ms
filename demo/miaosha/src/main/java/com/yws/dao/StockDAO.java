package com.yws.dao;

import com.yws.entity.Stock;

import java.util.List;

public interface StockDAO {

    //根据商品id查询库存信息的方法
    Stock checkStock(Integer id);

    //根据商品id扣除库存
    int updateSale(Stock stock);

    //取消订单恢复商品销售量
    int restoreSale(Stock stock);

    //添加商品
    int addStock(Stock stock);

    List<Stock> getStock(String name);
}
