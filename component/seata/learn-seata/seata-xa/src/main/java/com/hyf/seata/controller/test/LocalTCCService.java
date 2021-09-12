package com.hyf.seata.controller.test;

import io.seata.rm.tcc.api.LocalTCC;
import io.seata.rm.tcc.api.TwoPhaseBusinessAction;
import org.springframework.stereotype.Service;

/**
 * @author baB_hyf
 * @date 2021/08/31
 */
@Service
@LocalTCC
public class LocalTCCService {

    @TwoPhaseBusinessAction(name = "test",
            commitMethod = "commit", rollbackMethod = "rollback")
    public void test() {
        System.out.println("LocalTCC business");
    }

    public void commit() {
        System.out.println("LocalTCC commit");
    }

    public void rollback() {
        System.out.println("LocalTCC rollback");
    }
}
