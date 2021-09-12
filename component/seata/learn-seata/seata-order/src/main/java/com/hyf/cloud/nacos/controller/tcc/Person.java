package com.hyf.cloud.nacos.controller.tcc;

import io.seata.rm.tcc.api.BusinessActionContextParameter;

/**
 * @author baB_hyf
 * @date 2021/09/05
 */
public class Person {

    @BusinessActionContextParameter(paramName = "userId")
    private final String userId;

    @BusinessActionContextParameter(paramName = "inner", isParamInProperty = true)
    private final Inner inner;

    public Person(String userId, Inner inner) {
        this.userId = userId;
        this.inner = inner;
    }

    public String getUserId() {
        return userId;
    }

    public Inner getInner() {
        return inner;
    }

    public static class Inner {

        @BusinessActionContextParameter
        private final String innerText;

        public Inner(String innerText) {
            this.innerText = innerText;
        }

        public String getInnerText() {
            return innerText;
        }
    }
}
