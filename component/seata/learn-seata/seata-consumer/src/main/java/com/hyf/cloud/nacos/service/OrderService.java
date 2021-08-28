package com.hyf.cloud.nacos.service;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * @author baB_hyf
 * @date 2021/08/24
 */
@FeignClient("${order-service-name}")
@RequestMapping("order")
public interface OrderService {

    @RequestMapping("create")
    boolean create(@RequestParam("userId") String userId,
                   @RequestParam("storeId") String storeId,
                   @RequestParam("number") Integer number);
}
