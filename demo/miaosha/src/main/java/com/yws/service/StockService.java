package com.yws.service;

import com.yws.entity.Stock;

import java.util.List;

public interface StockService {

    int addStock(String name, Integer count);

    List<Stock> getStock(String name);

    int transactionTest() throws Exception;
}
