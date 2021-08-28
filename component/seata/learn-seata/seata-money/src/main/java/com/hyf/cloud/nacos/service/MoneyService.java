package com.hyf.cloud.nacos.service;

import com.hyf.cloud.nacos.mapper.MoneyMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;

/**
 * @author baB_hyf
 * @date 2021/08/24
 */
@Service
public class MoneyService {

    @Resource
    private MoneyMapper moneyMapper;

    public boolean deduct(String userId, BigDecimal money) {
        // throwEx();
        return moneyMapper.deduct(userId, money);
    }

    public void throwEx() {
        throw new RuntimeException("money");
    }
}
