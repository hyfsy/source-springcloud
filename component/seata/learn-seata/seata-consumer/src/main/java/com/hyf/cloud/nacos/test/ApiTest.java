package com.hyf.cloud.nacos.test;

import io.seata.core.context.RootContext;
import io.seata.core.rpc.RpcContext;
import io.seata.tm.api.GlobalTransaction;
import io.seata.tm.api.GlobalTransactionContext;
import io.seata.tm.api.TransactionalExecutor;
import io.seata.tm.api.TransactionalTemplate;
import io.seata.tm.api.transaction.TransactionInfo;

/**
 * @author baB_hyf
 * @date 2021/08/26
 */
public class ApiTest {

    public static void main(String[] args) throws Throwable {
        GlobalTransaction globalTransaction = GlobalTransactionContext.getCurrentOrCreate();
        globalTransaction.begin();

        TransactionalTemplate transactionalTemplate = new TransactionalTemplate();
        Object business = transactionalTemplate.execute(new TransactionalExecutor() {
                                                            @Override
                                                            public Object execute() throws Throwable {
                                                                System.out.println("business");
                                                                return 111;
                                                            }

                                                            @Override
                                                            public TransactionInfo getTransactionInfo() {
                                                                return null;
                                                            }
                                                        }
        );
        System.out.println(business);

        RootContext.bind("test");
        String xid = RootContext.getXID();
        RootContext.inGlobalTransaction();
        RootContext.unbind();

        RpcContext rpcContext = new RpcContext();
    }
}
