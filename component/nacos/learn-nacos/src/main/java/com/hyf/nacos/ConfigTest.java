package com.hyf.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;

import java.util.Properties;
import java.util.concurrent.Executor;

/**
 * @author baB_hyf
 * @date 2021/07/25
 */
public class ConfigTest {

    public static void main(String[] args) throws NacosException, InterruptedException {
        String serverAddr = "127.0.0.1:8848";
        String dataId = "example";
        String group = "DEFAULT_GROUP";

        Properties properties = new Properties();
        properties.setProperty("serverAddr", serverAddr);
        // 鉴权
        properties.put("username", "nacos");
        properties.put("password", "nacos");
        ConfigService configService = NacosFactory.createConfigService(properties);

        // 获取配置文本
        String config = configService.getConfig(dataId, group, 3000);
        System.out.println("Get init config: " + config);

        // 监听配置变更
        configService.addListener(dataId, group, new Listener() {
            @Override
            public Executor getExecutor() {
                return null;
            }

            @Override
            public void receiveConfigInfo(String configInfo) {
                System.out.println("Get config info: " + configInfo);
            }
        });

        // 删除监听
        configService.removeListener(dataId, group, new Listener() {
            @Override
            public Executor getExecutor() {
                return null;
            }

            @Override
            public void receiveConfigInfo(String configInfo) {

            }
        });

        // 发布新配置 - 不会触发监听
        String content = "hello=changed hello value";
        boolean success = configService.publishConfig(dataId, group, content,
                ConfigType.getDefaultType().getType());
        System.out.println("publish success: " + success);

        // 关闭服务内的http线程池
        configService.shutDown();

        Thread.currentThread().join();
    }
}
