package com.hyf.cloud.nacos.controller;

import com.hyf.cloud.nacos.controller.restoverride.RestOverride;
import com.hyf.cloud.nacos.controller.globallock.GlobalLockService;
import com.hyf.cloud.nacos.controller.tcc.LocalTCCService;
import com.hyf.cloud.nacos.controller.tcc.Person;
import com.hyf.cloud.nacos.service.MoneyService;
import com.hyf.cloud.nacos.service.OrderService;
import com.hyf.cloud.nacos.service.OrderService2;
import io.seata.saga.engine.AsyncCallback;
import io.seata.saga.engine.StateMachineEngine;
import io.seata.saga.proctrl.ProcessContext;
import io.seata.saga.statelang.domain.ExecutionStatus;
import io.seata.saga.statelang.domain.StateMachineInstance;
import io.seata.spring.annotation.GlobalLock;
import io.seata.spring.annotation.GlobalTransactional;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author baB_hyf
 * @date 2021/08/23
 */
@RestController
@RequestMapping("order")
public class OrderController {

    @Resource
    private OrderService orderService;

    @Autowired
    private MoneyService moneyService;

    @Autowired(required = false)
    private OrderService2 orderServiceRemote;

    @Resource
    private GlobalLockService globalLockService;

    @Resource
    private LocalTCCService localTCCService;

    @Autowired(required = false)
    private StateMachineEngine stateMachineEngine;

    private AtomicBoolean circuit = new AtomicBoolean(false);

    // http://localhost:8329/order/create?userId=1&storeId=1&number=1&ex=1

    @GlobalTransactional
    @RequestMapping("create")
    public boolean create(@RequestParam String userId,
                          @RequestParam String storeId,
                          @RequestParam Integer number,
                          @RequestParam(value = "ex", required = false, defaultValue = "false") Boolean throwEx) {
        System.out.println("order");

        boolean success = orderService.create(userId, storeId, number);
        boolean success2 = true;//moneyService.deduct(userId, new BigDecimal(String.valueOf(100)), throwEx);
        return success && success2;
    }

    // http://localhost:8329/order/update?orderId=1&number=100&ex=1

    @RestOverride
    @GlobalTransactional
    @RequestMapping("update")
    public boolean update(@RequestParam String orderId,
                          @RequestParam String number,
                          @RequestParam(value = "ex", required = false, defaultValue = "false") Boolean throwEx) {
        System.out.println("order");

        boolean success = orderService.update(orderId, "100");
        if (circuit.get()) {
            return success;
        }

        circuit.set(true);
        boolean success2;
        try {
            success2 = orderServiceRemote.update(orderId, "200");
        } finally {
            circuit.set(false);
        }
        return success && success2;
    }

    // http://localhost:8329/order/lock

    // 标记本地的事务不能和分布式事务冲突，必须要获取到全局锁才能提交，否则直接报错
    // 简单使用，提高性能
    @GlobalLock
    @RequestMapping("lock")
    // public boolean testGlobalLock() {
    //     return globalLockService.test();
    // }
    public boolean lock() {
        return orderService.update("1", "1");
    }

    // http://localhost:8329/order/select

    @GlobalTransactional
    @RequestMapping("select")
    public void select() {
        orderService.selectById("1");
    }

    // http://localhost:8329/order/update_undo

    @GlobalTransactional
    @RequestMapping("update_undo")
    public void update_undo() {
        orderService.update("2", "111");
        if (true) {
            throw new RuntimeException("update_undo");
        }
    }

    // http://localhost:8329/order/tcc

    // @GlobalLock
    @GlobalTransactional
    @RequestMapping("tcc")
    public boolean tcc() {
        // testParam();
        localTCCService.prepare(null);
        return true;
    }

    public void testParam() {
        Person person = new Person("userId", new Person.Inner("innerText"));
        localTCCService.test("id", null, Arrays.asList("str", "str2"), person, "noUse");
    }

    // curl http://localhost:8329/order/saga
    // https://seata.io/zh-cn/docs/user/saga.html

    @RequestMapping("saga")
    public boolean saga() {
        String stateMachineName = "reduceStoreAndMoney";
        String tenantId = null;
        // methodParamMap
        Map<String, Object> startParams = new HashMap<>();
        startParams.put("test_1", "value1");
        startParams.put("test_loop_collection", Arrays.asList("1", "2", "3"));
        StateMachineInstance stateMachineInstance = stateMachineEngine
                .start(stateMachineName, tenantId, startParams);
        // stateMachineEngine.startAsync(stateMachineName, tenantId, startParams, getAsyncCallback());

        ExecutionStatus status = stateMachineInstance.getStatus();
        System.out.println(status);
        return true;
    }

    public AsyncCallback getAsyncCallback() {
        return new AsyncCallback() {
            @Override
            public void onFinished(ProcessContext context, StateMachineInstance stateMachineInstance) {
                System.out.println(context);
                System.out.println(stateMachineInstance);
            }

            @Override
            public void onError(ProcessContext context, StateMachineInstance stateMachineInstance, Exception exp) {
                System.out.println(context);
                System.out.println(stateMachineInstance);
                System.out.println(exp);
            }
        };
    }
}
