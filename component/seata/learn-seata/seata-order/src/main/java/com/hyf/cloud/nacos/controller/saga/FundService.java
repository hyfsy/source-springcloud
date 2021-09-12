package com.hyf.cloud.nacos.controller.saga;

import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * @author baB_hyf
 * @date 2021/09/05
 */
@Service("fundService")
public class FundService implements SagaService {

    @Override
    public boolean reduce(String businessId, Map<String, Object> params) {
        System.out.println("fund reduce");
        return false;
    }

    @Override
    public boolean compensate(String businessId, Map<String, Object> params) {
        System.out.println("fund compensate");
        return false;
    }
}
