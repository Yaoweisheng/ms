package com.yws.dao;

import com.yws.entity.MsgId;

public interface MsgIdDAO {
    int setnx(MsgId msgId);

    int exist(MsgId msgId);
}
