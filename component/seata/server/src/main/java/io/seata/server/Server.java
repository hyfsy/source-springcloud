/*
 *  Copyright 1999-2019 Seata.io Group.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package io.seata.server;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import io.seata.common.XID;
import io.seata.common.thread.NamedThreadFactory;
import io.seata.common.util.NetUtil;
import io.seata.core.constants.ConfigurationKeys;
import io.seata.core.rpc.ShutdownHook;
import io.seata.core.rpc.netty.NettyRemotingServer;
import io.seata.core.rpc.netty.NettyServerConfig;
import io.seata.server.coordinator.DefaultCoordinator;
import io.seata.server.env.ContainerHelper;
import io.seata.server.env.PortHelper;
import io.seata.server.metrics.MetricsManager;
import io.seata.server.session.SessionHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The type Server.
 *
 * @author slievrly
 */
public class Server {
    /**
     * The entry point of application.
     *
     * @param args the input arguments
     * @throws IOException the io exception
     */
    public static void main(String[] args) throws IOException {
        // get port first, use to logback.xml
        int port = PortHelper.getPort(args);
        System.setProperty(ConfigurationKeys.SERVER_PORT, Integer.toString(port));

        // create logger
        final Logger logger = LoggerFactory.getLogger(Server.class);
        if (ContainerHelper.isRunningInContainer()) {
            logger.info("The server is running in container.");
        }

        // 解析命令行参数
        //initialize the parameter parser
        //Note that the parameter parser should always be the first line to execute.
        //Because, here we need to parse the parameters needed for startup.
        ParameterParser parameterParser = new ParameterParser(args);

        // 指标注册
        //initialize the metrics
        MetricsManager.get().init();

        System.setProperty(ConfigurationKeys.STORE_MODE, parameterParser.getStoreMode());

        ThreadPoolExecutor workingThreads = new ThreadPoolExecutor(NettyServerConfig.getMinServerPoolSize(),
                NettyServerConfig.getMaxServerPoolSize(), NettyServerConfig.getKeepAliveTime(), TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(NettyServerConfig.getMaxTaskQueueSize()),
                new NamedThreadFactory("ServerHandlerThread", NettyServerConfig.getMaxServerPoolSize()), new ThreadPoolExecutor.CallerRunsPolicy());

        // netty服务器
        NettyRemotingServer nettyRemotingServer = new NettyRemotingServer(workingThreads);
        //server port
        nettyRemotingServer.setListenPort(parameterParser.getPort());
        // nodeId 创建
        UUIDGenerator.init(parameterParser.getServerNode());

        // 事务会话初始化
        //log store mode : file, db, redis
        SessionHolder.init(parameterParser.getStoreMode());

        // 事务协调器
        DefaultCoordinator coordinator = new DefaultCoordinator(nettyRemotingServer);
        coordinator.init();
        nettyRemotingServer.setHandler(coordinator);
        // register ShutdownHook
        ShutdownHook.getInstance().addDisposable(coordinator);
        ShutdownHook.getInstance().addDisposable(nettyRemotingServer);

        // ip、端口处理
        //127.0.0.1 and 0.0.0.0 are not valid here.
        if (NetUtil.isValidIp(parameterParser.getHost(), false)) {
            XID.setIpAddress(parameterParser.getHost());
        } else {
            XID.setIpAddress(NetUtil.getLocalIp());
        }
        XID.setPort(nettyRemotingServer.getListenPort());

        // 启动netty服务器
        try {
            nettyRemotingServer.init();
        } catch (Throwable e) {
            logger.error("nettyServer init error:{}", e.getMessage(), e);
            System.exit(-1);
        }

        System.exit(0);
    }
}
