package com.hyf.sentinel;

import com.alibaba.csp.sentinel.*;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayParamFlowItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.cluster.ClusterConstants;
import com.alibaba.csp.sentinel.cluster.ClusterStateManager;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientAssignConfig;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientConfig;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientConfigManager;
import com.alibaba.csp.sentinel.cluster.server.ServerConstants;
import com.alibaba.csp.sentinel.cluster.server.config.ClusterServerConfigManager;
import com.alibaba.csp.sentinel.cluster.server.config.ServerFlowConfig;
import com.alibaba.csp.sentinel.cluster.server.config.ServerTransportConfig;
import com.alibaba.csp.sentinel.context.ContextUtil;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.ClusterRuleConstant;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.authority.AuthorityRule;
import com.alibaba.csp.sentinel.slots.block.authority.AuthorityRuleManager;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.ClusterFlowConfig;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowClusterConfig;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowItem;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import com.alibaba.csp.sentinel.slots.system.SystemRule;
import com.alibaba.csp.sentinel.slots.system.SystemRuleManager;

import java.util.*;

/**
 * @author baB_hyf
 * @date 2021/09/14
 */
public class Hello {

    public static final String RESOURCE_NAME = "HelloWorld";

    public static void main(String[] args) {
        // defineClusterClient();

        // defineFlowRule();
        // defineSystemRule();
        defineGatewayFlowRule();
        // defineParamFlowRule();
        // defineDegradeRule();
        // defineAuthorityRule();

        defineResource();
    }

    public static void defineResource() {
        // ContextUtil.enter("ref-resource-name", "limit");
        // try {
        //     Entry entry = SphU.entry("ref-resource-name");
        //     entry.exit();
        // } catch (BlockException e) {
        //     e.printStackTrace();
        // }
        // ContextUtil.exit();

        do {
            // ContextUtil.enter("RRR", "limit");
            Entry entry = null;
            // Entry entry2 = null;
            try {
                // entry = SphU.entry(RESOURCE_NAME, EntryType.IN, 1, "p1");
                // entry2 = Env.sph.entryWithPriority("RESOURCE_NAME", EntryType.IN, 21, true);
                entry = SphU.asyncEntry(RESOURCE_NAME);

                System.out.println("hello world");

            } catch (BlockException e) {
                e.printStackTrace();
            } finally {
                if (entry != null) {
                    entry.exit();
                    // entry2.exit();
                }
            }
            // ContextUtil.exit();

            // try {
            //     Thread.sleep(100);
            // } catch (InterruptedException e) {
            //     e.printStackTrace();
            // }
        } while (false);
    }

    public static void defineClusterClient() {
        ClusterStateManager.applyState(ClusterStateManager.CLUSTER_CLIENT);
        ClusterClientConfigManager.applyNewConfig(new ClusterClientConfig()
                .setRequestTimeout(ClusterConstants.DEFAULT_REQUEST_TIMEOUT));
        ClusterClientConfigManager.applyNewAssignConfig(new ClusterClientAssignConfig()
                .setServerHost("localhost")
                .setServerPort(ClusterConstants.DEFAULT_CLUSTER_SERVER_PORT));
    }

    public static void defineClusterServer() {
        ClusterStateManager.applyState(ClusterStateManager.CLUSTER_SERVER);
        ClusterServerConfigManager.loadGlobalFlowConfig(new ServerFlowConfig()
                .setSampleCount(ServerFlowConfig.DEFAULT_SAMPLE_COUNT));
        ClusterServerConfigManager.loadServerNamespaceSet(
                Collections.singleton(ServerConstants.DEFAULT_NAMESPACE));
        ClusterServerConfigManager.loadGlobalTransportConfig(new ServerTransportConfig()
                .setPort(ClusterConstants.DEFAULT_CLUSTER_SERVER_PORT)
                .setIdleSeconds(ServerTransportConfig.DEFAULT_IDLE_SECONDS));
        // not support namespace yet
        // ClusterServerConfigManager.loadFlowConfig("default", new ServerFlowConfig().setSampleCount(2));
    }

    public static void defineFlowRule() {
        List<FlowRule> flowRuleList = new ArrayList<>();

        FlowRule flowRule = new FlowRule();
        flowRule.setResource(RESOURCE_NAME);
        flowRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        flowRule.setCount(20); // 20 QPS
        flowRule.setWarmUpPeriodSec(10);
        flowRule.setLimitApp("limit"); // 流控针对的调用来源,default表示不区分
        flowRule.setStrategy(RuleConstant.STRATEGY_RELATE);
        flowRule.setRefResource("ref-resource-name");
        flowRule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_WARM_UP_RATE_LIMITER);
        flowRule.setClusterMode(true);
        ClusterFlowConfig clusterFlowConfig = new ClusterFlowConfig();
        clusterFlowConfig.setFlowId(1L);
        clusterFlowConfig.setSampleCount(10);
        clusterFlowConfig.setStrategy(ClusterRuleConstant.FLOW_CLUSTER_STRATEGY_NORMAL);
        flowRule.setClusterConfig(clusterFlowConfig);
        flowRuleList.add(flowRule);

        FlowRuleManager.loadRules(flowRuleList);
    }

    public static void defineSystemRule() {
        List<SystemRule> systemRuleList = new ArrayList<>();

        SystemRule systemRule = new SystemRule();
        systemRule.setResource(RESOURCE_NAME);
        systemRule.setQps(1);
        systemRule.setLimitApp("default");
        systemRuleList.add(systemRule);

        SystemRuleManager.loadRules(systemRuleList);
    }

    public static void defineGatewayFlowRule() {
        Set<GatewayFlowRule> gatewayFlowRuleSet = new HashSet<>();

        GatewayFlowRule gatewayFlowRule = new GatewayFlowRule();
        gatewayFlowRule.setResource(RESOURCE_NAME);
        gatewayFlowRule.setParamItem(new GatewayParamFlowItem().setFieldName("p1"));
        gatewayFlowRule.setCount(1);
        gatewayFlowRule.setBurst(1);
        gatewayFlowRule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_RATE_LIMITER);
        gatewayFlowRuleSet.add(gatewayFlowRule);

        GatewayRuleManager.loadRules(gatewayFlowRuleSet);
    }

    public static void defineParamFlowRule() {
        List<ParamFlowRule> paramFlowRuleList = new ArrayList<>();

        ParamFlowRule paramFlowRule = new ParamFlowRule();
        paramFlowRule.setResource(RESOURCE_NAME);
        paramFlowRule.setCount(1);
        paramFlowRule.setParamIdx(0);
        // 仅仅是特殊处理count的，实际的比较param由index来处理
        paramFlowRule.setParamFlowItemList(Collections.singletonList(new ParamFlowItem("p2", 1, "java.lang.String")));
        // paramFlowRule.setGrade(RuleConstant.FLOW_GRADE_THREAD);
        paramFlowRule.setClusterMode(true);
        ParamFlowClusterConfig clusterFlowConfig = new ParamFlowClusterConfig();
        clusterFlowConfig.setFlowId(2L);
        clusterFlowConfig.setSampleCount(10);
        clusterFlowConfig.setFallbackToLocalWhenFail(true);
        // 单机均摊 | 全局阈值
        clusterFlowConfig.setThresholdType(ClusterRuleConstant.FLOW_THRESHOLD_AVG_LOCAL);
        clusterFlowConfig.setWindowIntervalMs(1000);
        paramFlowRule.setClusterConfig(clusterFlowConfig);
        paramFlowRuleList.add(paramFlowRule);

        ParamFlowRuleManager.loadRules(paramFlowRuleList);
    }

    public static void defineDegradeRule() {
        List<DegradeRule> degradeRuleList = new ArrayList<>();

        DegradeRule degradeRule = new DegradeRule();
        degradeRule.setResource(RESOURCE_NAME);
        degradeRule.setCount(1);
        degradeRule.setMinRequestAmount(1);
        degradeRule.setSlowRatioThreshold(1.0);
        degradeRule.setTimeWindow(1); // 恢复时间 s
        degradeRule.setStatIntervalMs(1000);
        degradeRuleList.add(degradeRule);

        DegradeRuleManager.loadRules(degradeRuleList);
    }

    public static void defineAuthorityRule() {
        List<AuthorityRule> authorityRuleList = new ArrayList<>();

        AuthorityRule authorityRule = new AuthorityRule();
        authorityRule.setResource(RESOURCE_NAME);
        authorityRule.setLimitApp("default");
        authorityRule.setStrategy(RuleConstant.AUTHORITY_BLACK);
        authorityRuleList.add(authorityRule);

        AuthorityRuleManager.loadRules(authorityRuleList);
    }
}
