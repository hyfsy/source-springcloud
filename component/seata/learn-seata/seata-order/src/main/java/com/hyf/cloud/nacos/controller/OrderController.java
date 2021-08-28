package com.hyf.cloud.nacos.controller;

import com.hyf.cloud.nacos.service.MoneyService;
import com.hyf.cloud.nacos.service.OrderService;
import io.seata.spring.annotation.GlobalTransactional;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.math.BigDecimal;

/**
 * @author baB_hyf
 * @date 2021/08/23
 */
@RestController
@RequestMapping("order")
public class OrderController {

    @Resource
    private OrderService orderService;

    @Resource
    private MoneyService moneyService;

    // http://localhost:8329/order/create?userId=1&storeId=1&number=1&ex=0

    @GlobalTransactional
    @RequestMapping("create")
    public boolean create(@RequestParam String userId,
                          @RequestParam String storeId,
                          @RequestParam Integer number,
                          @RequestParam(value = "ex", required = false, defaultValue = "false") Boolean throwEx) {
        System.out.println("order");

        boolean success = orderService.create(userId, storeId, number);
        boolean success2 = moneyService.deduct(userId, new BigDecimal(String.valueOf(100)), throwEx);
        return success && success2;
    }
}
