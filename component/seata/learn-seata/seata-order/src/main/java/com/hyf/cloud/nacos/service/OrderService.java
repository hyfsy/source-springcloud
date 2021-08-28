package com.hyf.cloud.nacos.service;

import com.hyf.cloud.nacos.mapper.OrderMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * @author baB_hyf
 * @date 2021/08/24
 */
@Service
public class OrderService {

    @Resource
    private OrderMapper orderMapper;

    public boolean create(String userId, String storeId, Integer number) {
        // throwEx();
        return orderMapper.create(userId, storeId, number);
    }

    public void throwEx() {
        throw new RuntimeException("order");
    }
}
