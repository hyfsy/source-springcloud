package com.hyf.cloud.nacos.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

/**
 * @author baB_hyf
 * @date 2021/08/25
 */
@Mapper
public interface OrderMapper {

    boolean create(@Param("userId") String userId,
                   @Param("storeId") String storeId,
                   @Param("number") Integer number);

    boolean delete(@Param("orderId") String orderId);

    boolean update(@Param("orderId") String orderId, @Param("number") String number);

    Map<String, Object> selectById(@Param("orderId") String orderId);
}
