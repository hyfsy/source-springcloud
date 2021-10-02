package com.hyf.sentinel;

import com.alibaba.csp.sentinel.cluster.flow.rule.ClusterFlowRuleManager;
import com.alibaba.csp.sentinel.cluster.flow.rule.ClusterParamFlowRuleManager;
import com.alibaba.csp.sentinel.cluster.server.ServerConstants;
import com.alibaba.csp.sentinel.slots.block.flow.ClusterFlowConfig;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowClusterConfig;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;

import java.util.ArrayList;
import java.util.List;

/**
 * @author baB_hyf
 * @date 2021/09/25
 */
public class Server {

    public static void main(String[] args) {
        // ClusterFlowRuleManager.loadRules(ServerConstants.DEFAULT_NAMESPACE, getFlowRuleList());
        ClusterParamFlowRuleManager.loadRules(ServerConstants.DEFAULT_NAMESPACE, getParamFlowRuleList());
        // ConcurrentClusterFlowChecker.acquireConcurrentToken(); // not use
        Hello.defineClusterServer();
    }

    public static List<FlowRule> getFlowRuleList() {
        return new ArrayList<FlowRule>() {
            {
                FlowRule flowRule = new FlowRule();
                flowRule.setClusterMode(true);
                ClusterFlowConfig clusterFlowConfig = new ClusterFlowConfig();
                clusterFlowConfig.setFlowId(1L);
                clusterFlowConfig.setWindowIntervalMs(1000);
                flowRule.setClusterConfig(clusterFlowConfig);
                flowRule.setResource("RRR");
                add(flowRule);
            }
        };
    }

    public static List<ParamFlowRule> getParamFlowRuleList() {
        return new ArrayList<ParamFlowRule>() {
            {
                ParamFlowRule paramFlowRule = new ParamFlowRule();
                paramFlowRule.setParamIdx(0);
                paramFlowRule.setClusterMode(true);
                ParamFlowClusterConfig paramFlowClusterConfig = new ParamFlowClusterConfig();
                paramFlowClusterConfig.setFlowId(2L);
                paramFlowClusterConfig.setWindowIntervalMs(1000);
                paramFlowRule.setClusterConfig(paramFlowClusterConfig);
                paramFlowRule.setResource("RRR");
                add(paramFlowRule);
            }
        };
    }
}
