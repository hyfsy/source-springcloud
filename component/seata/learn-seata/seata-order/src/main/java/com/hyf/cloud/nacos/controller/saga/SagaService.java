package com.hyf.cloud.nacos.controller.saga;

import java.util.Map;

/**
 * @author baB_hyf
 * @date 2021/09/05
 */
public interface SagaService {

    boolean reduce(String businessId, Map<String, Object> params);

    boolean compensate(String businessId, Map<String, Object> params);
}
