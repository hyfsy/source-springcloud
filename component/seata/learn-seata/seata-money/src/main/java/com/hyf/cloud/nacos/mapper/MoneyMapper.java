package com.hyf.cloud.nacos.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

/**
 * @author baB_hyf
 * @date 2021/08/25
 */
@Mapper
public interface MoneyMapper {

    boolean deduct(@Param("userId") String userId,
                   @Param("money") BigDecimal money);
}
