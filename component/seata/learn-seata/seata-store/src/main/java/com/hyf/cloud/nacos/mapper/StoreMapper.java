package com.hyf.cloud.nacos.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * @author baB_hyf
 * @date 2021/08/25
 */
@Mapper
public interface StoreMapper {

    boolean reduce(@Param("storeId") String storeId,
                   @Param("number") Integer number);
}
