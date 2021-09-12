package com.hyf.cloud.nacos.controller.saga;

import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * @author baB_hyf
 * @date 2021/09/11
 */
@Service("loopService")
public class LoopService {

    public String loop(Map<String, Object> params) {
        int loopCounter = (int) params.get("loopCounter");
        String loopElement = (String) params.get("loopElement");

        if (loopCounter == 0) {
            throw new RuntimeException("test loop ex");
        }

        System.out.println(loopCounter);
        System.out.println(loopElement);

        return loopElement + " - " + loopCounter;
    }
}
