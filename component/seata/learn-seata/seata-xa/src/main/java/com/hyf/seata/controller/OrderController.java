// package com.hyf.seata.controller;
//
// import com.hyf.seata.controller.restoverride.RestOverride;
// import com.hyf.seata.controller.test.GlobalLockService;
// import com.hyf.seata.service.MoneyService;
// import com.hyf.seata.service.OrderService;
// import com.hyf.seata.service.OrderService2;
// import io.seata.spring.annotation.GlobalLock;
// import io.seata.spring.annotation.GlobalTransactional;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.web.bind.annotation.RequestMapping;
// import org.springframework.web.bind.annotation.RequestParam;
// import org.springframework.web.bind.annotation.RestController;
//
// import javax.annotation.Resource;
// import java.util.concurrent.atomic.AtomicBoolean;
//
// /**
//  * @author baB_hyf
//  * @date 2021/08/23
//  */
// @RestController
// @RequestMapping("order")
// public class OrderController {
//
//     @Resource
//     private OrderService orderService;
//
//     @Resource
//     private MoneyService moneyService;
//
//     @Autowired(required = false)
//     private OrderService2 orderServiceRemote;
//
//     @Resource
//     private GlobalLockService globalLockService;
//
//     private AtomicBoolean circuit = new AtomicBoolean(false);
//
//     // http://localhost:8329/order/create?userId=1&storeId=1&number=1&ex=1
//
//     @GlobalTransactional
//     @RequestMapping("create")
//     public boolean create(@RequestParam String userId,
//                           @RequestParam String storeId,
//                           @RequestParam Integer number,
//                           @RequestParam(value = "ex", required = false, defaultValue = "false") Boolean throwEx) {
//         System.out.println("order");
//
//         boolean success = orderService.create(userId, storeId, number);
//         boolean success2 = true;//moneyService.deduct(userId, new BigDecimal(String.valueOf(100)), throwEx);
//         return success && success2;
//     }
//
//     // http://localhost:8329/order/update?orderId=1&number=100&ex=1
//
//     @RestOverride
//     @GlobalTransactional
//     @RequestMapping("update")
//     public boolean update(@RequestParam String orderId,
//                           @RequestParam String number,
//                           @RequestParam(value = "ex", required = false, defaultValue = "false") Boolean throwEx) {
//         System.out.println("order");
//
//         boolean success = orderService.update(orderId, "100");
//         if (circuit.get()) {
//             return success;
//         }
//
//         circuit.set(true);
//         boolean success2;
//         try {
//             success2 = orderServiceRemote.update(orderId, "200");
//         } finally {
//             circuit.set(false);
//         }
//         return success && success2;
//     }
//
//     // http://localhost:8329/order/lock
//
//     // 标记本地的事务不能和分布式事务冲突，必须要获取到全局锁才能提交，否则直接报错
//     // 简单使用，提高性能
//     @GlobalLock
//     @RequestMapping("lock")
//     // public boolean testGlobalLock() {
//     //     return globalLockService.test();
//     // }
//     public boolean lock() {
//         return orderService.update("1", "1");
//     }
//
//     // http://localhost:8329/order/select
//
//     @GlobalTransactional
//     @RequestMapping("select")
//     public void select() {
//         orderService.selectById("1");
//     }
//
//     // http://localhost:8329/order/update_undo
//
//     @GlobalTransactional
//     @RequestMapping("update_undo")
//     public void update_undo() {
//         orderService.update("2", "111");
//         if (true) {
//             throw new RuntimeException("update_undo");
//         }
//     }
// }
