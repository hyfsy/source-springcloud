package com.hyf.cloud.nacos.controller.saga;

import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * @author baB_hyf
 * @date 2021/09/05
 */
@Service("storeService")
public class StoreService implements SagaService {

    @Override
    public boolean reduce(String businessId, Map<String, Object> params) {
        System.out.println("store reduce");
        return false;
    }

    @Override
    public boolean compensate(String businessId, Map<String, Object> params) {
        System.out.println("store compensate");
        return false;
    }
}
