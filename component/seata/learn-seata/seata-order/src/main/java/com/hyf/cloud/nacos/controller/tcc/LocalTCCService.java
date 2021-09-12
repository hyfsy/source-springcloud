package com.hyf.cloud.nacos.controller.tcc;

import com.hyf.cloud.nacos.service.OrderService;
import io.seata.rm.tcc.TwoPhaseResult;
import io.seata.rm.tcc.api.BusinessActionContext;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * @author baB_hyf
 * @date 2021/08/31
 */
@Service
public class LocalTCCService implements TCCService {

    public static final String OP_KEY    = "orderId";
    public static final String OP_RESULT = "prepareResult";

    @Resource
    private OrderService orderService;

    // TODO 注意，这边的ctx已经是不能修改的了
    @Override
    public void prepare(BusinessActionContext businessActionContext) {
        String orderId = "11";

        Map<String, Object> actionContext = businessActionContext.getActionContext();
        actionContext.put(OP_KEY, orderId);

        // 锁定资源 select ... for update
        orderService.selectById(orderId); // 可以把查询结果放在 ctx 里
        Integer resultNumber = 100;
        actionContext.put(OP_RESULT, resultNumber);
        if (true) {
            throw new RuntimeException("xxx");
        }
    }

    @Override
    public TwoPhaseResult commit(BusinessActionContext businessActionContext) {
        Object orderId = businessActionContext.getActionContext(OP_KEY);
        if (orderId != null) {
            orderService.update(orderId.toString(), "200");
            return new TwoPhaseResult(true, "msg not use"); // 标记commit是否成功
        }
        return new TwoPhaseResult(false, null);
    }

    @Override
    public void rollback(BusinessActionContext businessActionContext) {
        Object orderId = businessActionContext.getActionContext(OP_KEY);
        Object prepareResult = businessActionContext.getActionContext(OP_RESULT);
        if (orderId != null && prepareResult != null) {
            orderService.update(prepareResult.toString(), orderId.toString());
        }
    }

    @Override
    public boolean rollback2() {
        return true;
    }

    @Override
    public void test(String id, BusinessActionContext businessActionContext,
                     List<String> strList, Person person, String notUse) {
        System.out.println("LocalTCC business");
        orderService.create("11", "11", 100);
        System.out.println("business finished");
    }
}
