package com.hyf.seata.service;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

/**
 * @author baB_hyf
 * @date 2021/08/24
 */
@FeignClient("${money-service-name}")
@RequestMapping("money")
public interface MoneyService {

    @RequestMapping("deduct")
    boolean deduct(@RequestParam("userId") String userId,
                   @RequestParam("money") BigDecimal money,
                   @RequestParam(value = "ex", required = false, defaultValue = "false") Boolean throwEx);
}
