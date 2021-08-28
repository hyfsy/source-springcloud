package com.hyf.cloud.nacos.service;

import io.seata.spring.annotation.GlobalTransactional;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * @author baB_hyf
 * @date 2021/08/25
 */
@Service
public class ConsumerService {

    @Resource
    private StoreService storeService;

    @Resource
    private OrderService orderService;

    @GlobalTransactional
    public boolean consumer(String userId, String storeId, Integer number) {

        boolean reduce = storeService.reduce(storeId, number);
        boolean create = orderService.create(userId, storeId, number);

        return reduce && create;
    }
}
