package com.hyf.sentinel;

import com.alibaba.csp.sentinel.*;
import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.context.ContextUtil;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.authority.AuthorityRule;
import com.alibaba.csp.sentinel.slots.block.authority.AuthorityRuleManager;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowItem;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import com.alibaba.csp.sentinel.slots.statistic.StatisticSlotCallbackRegistry;
import com.alibaba.csp.sentinel.slots.system.SystemRule;
import com.alibaba.csp.sentinel.slots.system.SystemRuleManager;

import java.util.Collections;
import java.util.concurrent.ForkJoinPool;

/**
 * @author baB_hyf
 * @date 2021/09/14
 */
public class SentinelTest {

    public static void main(String[] args) {

        // BlockException.isBlockException(new Exception());
    }

    public void sphUTest() {
        // 若 entry 的时候传入了热点参数，那么 exit 的时候也一定要带上对应的参数
        Entry entry = null;
        try {
            // 若需要配置例外项，则传入的参数只支持基本类型
            // EntryType 代表流量类型，其中系统规则只对 IN 类型的埋点生效
            // count 大多数情况都填 1，代表统计为一次调用
            entry = SphU.entry("ResourceName", EntryType.IN, 1, "param1", "param2" /* 热点参数 */);
        } catch (BlockException e) {
            // Tracer.trace(ex); // 不能在try-with-resources中的catch中使用
            e.printStackTrace();
        } catch (Exception e) {
            // 若需要配置降级规则，需要通过这种方式记录业务异常
            Tracer.traceEntry(e, entry);
        } finally {
            if (entry != null) {
                entry.exit(1, "param1", "param2");
            }
        }
    }

    public void sphOTest() {
        if (SphO.entry("ResourceName")) {
            try {
                System.out.println("business");
            } finally {
                SphO.exit();
            }
        }
        else {
            // ...
        }
    }

    public void asyncEntry() {
        try {
            AsyncEntry entry = SphU.asyncEntry("ResourceName");
            Runnable r = () -> {
                try {
                    // business
                } finally {
                    entry.exit();
                }
            };

            ForkJoinPool.commonPool().submit(r);
        } catch (BlockException e) {
            e.printStackTrace();
        }
    }

    public void contextSwitch() {

        Runnable runnable = () -> {
            Entry entry = null;
            try {
                entry = SphU.entry("R");
            } catch (BlockException e) {
                e.printStackTrace();
            } finally {
                entry.exit();
            }
        };


        AsyncEntry asyncEntry = null;
        try {
            asyncEntry = SphU.asyncEntry("R");

            ContextUtil.runOnContext(asyncEntry.getAsyncContext(), () -> {
                runnable.run();
            });

        } catch (BlockException blockException) {
            blockException.printStackTrace();
        } finally {
            asyncEntry.exit();
        }
    }

    // 限流
    public void flowRule() {
        FlowRule rule = new FlowRule();
        rule.setResource("RESOURCE_NAME");
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(20); // 20 QPS
        rule.setLimitApp("default"); // 流控针对的调用来源,default表示不区分
        rule.setClusterMode(false); // 是否集群限流
        rule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT); // 流控效果（直接拒绝/WarmUp/匀速+排队等待）
        rule.setStrategy(RuleConstant.STRATEGY_DIRECT); // 调用关系限流策略：直接、链路、关联
        FlowRuleManager.loadRules(Collections.singletonList(rule));
    }

    // 熔断降级
    public void degradeRule() {
        DegradeRule rule = new DegradeRule();
        rule.setResource("R");
        rule.setGrade(RuleConstant.DEGRADE_GRADE_RT); // 熔断策略，支持慢调用比例/异常比例/异常数策略
        rule.setCount(1);
        rule.setTimeWindow(1); // s
        rule.setMinRequestAmount(5); // 熔断触发的最小请求数，请求数小于该值时即使异常比率超出阈值也不会熔断
        rule.setStatIntervalMs(1000); // 统计时长（单位为 ms）
        rule.setSlowRatioThreshold(1); // 慢调用比例阈值，仅慢调用比例模式有效
        DegradeRuleManager.loadRules(Collections.singletonList(rule));
    }

    // 系统规则
    public void systemRule() {
        // 系统规则只针对入口资源（EntryType=IN）生效
        SystemRule rule = new SystemRule();
        rule.setResource("R");
        rule.setHighestSystemLoad(-1); // 1 触发值，用于触发自适应控制阶段
        rule.setAvgRt(-1); // 所有入口流量的平均响应时间
        rule.setMaxThread(-1); // 入口流量的最大并发数
        rule.setQps(-1); // 所有入口资源的 QPS
        rule.setHighestCpuUsage(-1); // 当前系统的 CPU 使用率（0.0-1.0）
        SystemRuleManager.loadRules(Collections.singletonList(rule));
    }

    // 访问控制规则
    public void authorityRule() {
        AuthorityRule rule = new AuthorityRule();
        rule.setResource("R");
        rule.setLimitApp("default"); // 对应的黑名单/白名单，不同 origin 用 , 分隔，如 appA,appB
        rule.setStrategy(RuleConstant.AUTHORITY_BLACK);
        AuthorityRuleManager.loadRules(Collections.singletonList(rule));
    }

    // 热点参数规则
    public void paramFlowRule() {
        ParamFlowRule rule = new ParamFlowRule();
        rule.setParamIdx(1);
        rule.setBurstCount(1);
        ParamFlowRuleManager.loadRules(Collections.singletonList(rule));

        ParamFlowItem paramFlowItem = new ParamFlowItem()
                .setObject("PARAM_B")
                .setClassType(int.class.getName())
                .setCount(10);
        rule.setParamFlowItemList(Collections.singletonList(paramFlowItem));
    }

    public void http() {
        // http://localhost:8719/getRules?type=<XXX>
        // flow/degrade/system...

        // http://localhost:8719/getParamRules

        // 如果规则生效，在返回的数据栏中的 block 以及 block(m) 中会有显示
        // http://localhost:8719/cnode?id=<资源名称>，观察返回的数据。
    }

    public void exception() {
        // 父类：BlockException

        // 流控异常：FlowException
        // 熔断降级异常：DegradeException
        // 系统保护异常：SystemBlockException
        // 热点参数限流异常：ParamFlowException

        BlockException.isBlockException(null);
    }

    public void callback() {
        // onPass/onBlock
        StatisticSlotCallbackRegistry.addEntryCallback(null, null);
        // onExit 资源获取成功且正常结束
        StatisticSlotCallbackRegistry.addExitCallback(null, null);
    }

    public void util() {
        // 向传入entry对应的资源 记录业务异常（非 BlockException 异常）
        // 通过 SphU 或 SphO 手动定义资源，则 Sentinel 不能感知上层业务的异常，否则对应的异常不会统计到 Sentinel 异常计数中
        // 不要在 try-with-resources 形式的 SphU.entry(xxx) 中使用，否则会统计不上
        Tracer.traceEntry(new Exception(), null);

        Context context = ContextUtil.enter("ContextName", "origin");
        ContextUtil.runOnContext(context, () -> { }); // 常用于异步调用链路中 context 的变换
        ContextUtil.exit();

        Context ctx = ContextUtil.getContext();

    }
}
