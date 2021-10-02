package com.hyf.cloud.sentinel.config;

import com.alibaba.cloud.sentinel.annotation.SentinelRestTemplate;
import com.alibaba.cloud.sentinel.rest.SentinelClientHttpResponse;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestTemplate;

/**
 * @author baB_hyf
 * @date 2021/09/14
 */
@Configuration
public class RestTemplateConfiguration {

    @Bean
    @SentinelRestTemplate(blockHandlerClass = ExceptionUtil.class, blockHandler = "handleBlockException",
            fallbackClass = ExceptionUtil.class, fallback = "handleFallbackException",
            urlCleanerClass = ExceptionUtil.class, urlCleaner = "handleUrlCleaner")
    public RestTemplate restTemplateSentinel() {
        return new RestTemplate();
    }

    // ClientHttpRequestInterceptor
    public static class ExceptionUtil {

        // 在原方法被限流/降级/系统保护的时候调用
        public static ClientHttpResponse handleBlockException(HttpRequest request, byte[] body, ClientHttpRequestExecution execution, BlockException blockException) {
            return new SentinelClientHttpResponse();
        }

        // 会针对所有类型的异常
        public static ClientHttpResponse handleFallbackException(HttpRequest request, byte[] body, ClientHttpRequestExecution execution, BlockException blockException) {
            return new SentinelClientHttpResponse();
        }

        public static String handleUrlCleaner(String url) {
            // 资源清洗，过滤掉不想处理的url（返回空串 ""）
            return url;
        }
    }
}
