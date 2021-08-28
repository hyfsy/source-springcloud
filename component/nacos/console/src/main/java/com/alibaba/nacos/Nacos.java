/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.File;
import java.nio.file.Paths;

/**
 * Nacos starter.
 *
 * @author nacos
 */
@SpringBootApplication(scanBasePackages = "com.alibaba.nacos")
@ServletComponentScan
@EnableScheduling
public class Nacos {

    public static void main(String[] args) {
        // -Dnacos.standalone=true
        // -DembeddedStorage=true
        // -Dnacos.functionMode=config
        // -Dnacos.functionMode=naming
        // all
        
//        System.setProperty("upgraded", "true");

//        System.setProperty("nacos.standalone", "true"); // 单机 or 集群
        System.setProperty("nacos.standalone", "false");
        System.setProperty("nacos.naming.use-new-raft.first", "true"); // 指定使用新版的jraft协议（旧版为自己实现的，非jraft）

        String path = Nacos.class.getResource("/").getPath();
        String classesPath = path.substring(1, path.length() - 1);
        System.setProperty("nacos.home", classesPath);
        
        // 日志存储路径
        String logPath = classesPath + File.separator + "logs";
        System.setProperty("nacos.logs.path", logPath);
        SpringApplication.run(Nacos.class, args);
    }
}

