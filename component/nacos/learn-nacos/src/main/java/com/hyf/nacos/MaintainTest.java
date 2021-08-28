package com.hyf.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingMaintainService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.Service;
import com.alibaba.nacos.api.selector.ExpressionSelector;

/**
 * @author baB_hyf
 * @date 2021/07/25
 */
public class MaintainTest {

    public static void main(String[] args) throws NacosException {

        String serverAddr = "127.0.0.1:8848";
        String serviceName = "example-service";
        String groupName = "DEFAULT_GROUP";

        // 获取维护服务对象
        NamingMaintainService maintainService = NacosFactory.createMaintainService(serverAddr);

        // 创建服务
        maintainService.createService(serviceName, groupName, 2.0F, "label=example");

        // 删除服务
        maintainService.deleteService(serviceName, groupName);

        // 查询服务
        maintainService.queryService(serviceName, groupName);

        // 更新服务
        maintainService.updateService(new Service(), new ExpressionSelector());

        // 更新实例
        maintainService.updateInstance(serviceName, groupName, new Instance());

        // 关闭服务内的http线程池
        maintainService.shutDown();
    }
}
