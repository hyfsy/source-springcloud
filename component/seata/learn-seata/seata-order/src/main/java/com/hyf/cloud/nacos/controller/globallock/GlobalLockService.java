package com.hyf.cloud.nacos.controller.globallock;

import io.seata.spring.annotation.GlobalLock;
import org.springframework.stereotype.Service;

/**
 * @author baB_hyf
 * @date 2021/08/31
 */
@Service
@GlobalLock
public class GlobalLockService {

    @GlobalLock // 方法优先
    public boolean test() {
        System.out.println("GlobalLock business");
        return true;
    }
}
