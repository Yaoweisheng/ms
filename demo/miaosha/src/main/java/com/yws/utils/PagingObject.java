package com.yws.utils;

import lombok.Data;

import java.util.List;

@Data
public class PagingObject<T> {
    public PagingObject(List<T> list, Integer count){
        this.list = list;
        this.count = count;
    }
    private List<T> list;
    private Integer count;
}
