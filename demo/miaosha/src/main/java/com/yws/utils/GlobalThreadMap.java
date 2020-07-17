package com.yws.utils;

import java.util.concurrent.ConcurrentHashMap;

public class GlobalThreadMap {
    public static ConcurrentHashMap parkThreadMap = new ConcurrentHashMap();
}
