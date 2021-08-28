package com.hyf.cloud.nacos.controller;

import com.hyf.cloud.nacos.service.ConsumerService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * @author baB_hyf
 * @date 2021/08/23
 */
@RestController
public class SeataController {

    @Resource
    private ConsumerService consumerService;

    @RequestMapping("consumer")
    public boolean consumer(@RequestParam String userId,
                            @RequestParam String storeId,
                            @RequestParam Integer number) {

        return consumerService.consumer(userId, storeId, number);
    }
}
