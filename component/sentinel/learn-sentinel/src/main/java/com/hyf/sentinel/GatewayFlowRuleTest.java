package com.hyf.sentinel;

import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;

/**
 * @author baB_hyf
 * @date 2021/09/15
 */
public class GatewayFlowRuleTest {

    public static void main(String[] args) {
        // 网关限流规则，针对 API Gateway 的场景定制的限流规则
        GatewayFlowRule rule = new GatewayFlowRule();

        rule.setCount(1);

        GatewayRuleManager.loadRules(null);
        GatewayRuleManager.register2Property(null);
    }
}
