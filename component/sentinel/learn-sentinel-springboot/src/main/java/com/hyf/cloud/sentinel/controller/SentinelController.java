package com.hyf.cloud.sentinel.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author baB_hyf
 * @date 2021/09/12
 */
@RestController
@RequestMapping("/consumer")
@SentinelResource(defaultFallback = "fallback")
public class SentinelController {

    public String fallback(BlockException e) {
        return "e";
    }

    @RequestMapping("/block")
    // 标识资源是否被限流、降级，consumer表示资源名
    @SentinelResource(value = "consumer", blockHandler = "blockHandleForConsumer")
    public String block(@RequestParam(required = false) String id) {
        System.out.println("success");
        return "success";
    }

    public String blockHandleForConsumer(String id, BlockException e) {
        return "Blocked";
    }

    @RequestMapping("/fallback")
    @SentinelResource(value = "fallback", fallback = "fallbackForConsumer")
    public String fallback(@RequestParam(required = false) String id) {
        System.out.println("success");
        if (id != null) {
            throw new RuntimeException(id);
        }
        return "success";
    }

    public String fallbackForConsumer(String id, BlockException e) {
        return "Fallback";
    }
}
