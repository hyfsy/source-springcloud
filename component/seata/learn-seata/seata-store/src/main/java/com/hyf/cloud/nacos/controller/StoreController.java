package com.hyf.cloud.nacos.controller;

import com.hyf.cloud.nacos.service.StoreService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * @author baB_hyf
 * @date 2021/08/23
 */
@RestController
@RequestMapping("store")
public class StoreController {

    @Resource
    private StoreService storeService;

    @RequestMapping("reduce")
    public boolean reduce(@RequestParam String storeId,
                          @RequestParam Integer number) {
        System.out.println("store");
        return storeService.reduce(storeId, number);
    }
}
