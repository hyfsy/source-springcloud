package com.hyf.cloud.nacos.controller.tcc;

import io.seata.rm.tcc.TwoPhaseResult;
import io.seata.rm.tcc.api.BusinessActionContext;
import io.seata.rm.tcc.api.BusinessActionContextParameter;
import io.seata.rm.tcc.api.LocalTCC;
import io.seata.rm.tcc.api.TwoPhaseBusinessAction;

import java.util.List;

/**
 * @author baB_hyf
 * @date 2021/09/05
 */
@LocalTCC
public interface TCCService {

    @TwoPhaseBusinessAction(name = "resource-id-test-prepare")
    void prepare(BusinessActionContext businessActionContext);

    TwoPhaseResult commit(BusinessActionContext businessActionContext);

    void rollback(BusinessActionContext businessActionContext);

    boolean rollback2();

    // 参数会放入 BusinessActionContext 中
    @TwoPhaseBusinessAction(name = "test", commitMethod = "commit", rollbackMethod = "rollback")
    void test(@BusinessActionContextParameter(paramName = "id") String id,
              BusinessActionContext businessActionContext,
              @BusinessActionContextParameter(index = 1) List<String> strList,
              @BusinessActionContextParameter(isParamInProperty = true) Person person,
              @BusinessActionContextParameter(isShardingParam = true) String notUse);
}
