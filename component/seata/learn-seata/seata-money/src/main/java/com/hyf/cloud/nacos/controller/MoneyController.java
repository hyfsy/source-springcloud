package com.hyf.cloud.nacos.controller;

import com.hyf.cloud.nacos.service.MoneyService;
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
@RequestMapping("money")
public class MoneyController {

    @Resource
    private MoneyService moneyService;

    @RequestMapping("deduct")
    public boolean deduct(@RequestParam String userId,
                          @RequestParam BigDecimal money,
                          @RequestParam(value = "ex", required = false, defaultValue = "false") Boolean throwEx) {
        System.out.println("money");

        if (throwEx) {
            throwEx();
        }

        return moneyService.deduct(userId, money);
    }

    public void throwEx() {
        throw new RuntimeException("money");
    }
}
