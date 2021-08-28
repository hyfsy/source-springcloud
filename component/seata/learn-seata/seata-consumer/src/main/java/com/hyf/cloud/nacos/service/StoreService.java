package com.hyf.cloud.nacos.service;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * @author baB_hyf
 * @date 2021/08/24
 */
@FeignClient("${store-service-name}")
@RequestMapping("store")
public interface StoreService {

    @RequestMapping("reduce")
    boolean reduce(@RequestParam("storeId") String storeId,
                   @RequestParam("number") Integer number);
}
