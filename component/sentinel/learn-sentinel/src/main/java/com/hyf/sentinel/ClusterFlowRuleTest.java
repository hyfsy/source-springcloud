package com.hyf.sentinel;

import com.alibaba.csp.sentinel.cluster.ClusterStateManager;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientConfigManager;
import com.alibaba.csp.sentinel.cluster.flow.rule.ClusterFlowRuleManager;
import com.alibaba.csp.sentinel.cluster.server.config.ClusterServerConfigManager;
import com.alibaba.csp.sentinel.datasource.Converter;
import com.alibaba.csp.sentinel.datasource.ReadableDataSource;
import com.alibaba.csp.sentinel.datasource.nacos.NacosDataSource;
import com.alibaba.csp.sentinel.property.DynamicSentinelProperty;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;

import java.util.List;

/**
 * @author baB_hyf
 * @date 2021/09/15
 */
public class ClusterFlowRuleTest {

    public static void main(String[] args) {
    }

    public void clusterClientRuleRegister() {

        // 动态规则发现

        String remoteAddress = "";
        String groupId = "";
        String dataId = "";
        Converter<String, List<FlowRule>> parser = null;
        ReadableDataSource<String, List<FlowRule>> flowRuleDataSource = new NacosDataSource<>(remoteAddress, groupId, dataId, parser);
        FlowRuleManager.register2Property(flowRuleDataSource.getProperty());

        // 引入 sentinel-cluster-client-default 依赖

        // API切换模式
        // http://<ip>:<port>/setClusterMode?mode=<xxx>
        // 0 client 1 server -1 close
        // 手动改变模式
        ClusterStateManager.applyState(ClusterStateManager.CLUSTER_CLIENT);

        // 连接日志
        // ~/logs/csp/sentinel-record.log

        // 动态配置源
        ClusterClientConfigManager.registerServerAssignProperty(null); // 要连接的对端 token server 地址
        ClusterClientConfigManager.registerClientConfigProperty(null); // 客户端通信配置
        // or http://<ip>:<port>/cluster/client/modifyConfig?data=<config>
        // <config> -> {xxx, xxx, xxx} see ClusterClientAssignConfig、ClusterClientConfig

    }

    //  namespace set 产生变更时，会自动针对新加入的 namespace 生成动态规则源并进行自动监听，并删除旧的不需要的规则源
    public void clusterServerRuleRegister() {

        // 动态规则发现

        ClusterFlowRuleManager.setPropertySupplier((namespace) -> {
            return new DynamicSentinelProperty<>();
        });

        // 引入 sentinel-cluster-server-default 依赖

        // 手动改变模式
        ClusterStateManager.applyState(ClusterStateManager.CLUSTER_SERVER);

        // 分为独立模式和内嵌模式
        // 独立模式下，可以直接创建对应的 ClusterTokenServer 实例并在 main 函数中通过 start 方法启动 Token Server

        // ClusterServerConfigManager.registerXXX 来注册相关的配置源
    }
}
