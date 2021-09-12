package com.hyf.cloud.nacos.controller.saga;

import org.springframework.stereotype.Service;

/**
 * @author baB_hyf
 * @date 2021/09/11
 */
@Service("compensateService")
public class CompensateService {

    public boolean execute() {
        System.out.println("test execute start");
        return false;
    }

    public boolean compensate() {
        System.out.println("test compensate success");
        return true;
    }
}
