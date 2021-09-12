package com.hyf.seata;

import com.mysql.cj.jdbc.MysqlXADataSource;
import com.mysql.cj.jdbc.MysqlXid;

import javax.sql.*;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Properties;

/**
 * @author baB_hyf
 * @date 2021/09/04
 */
public class XATest {

    private volatile XADataSource dataSource;

    public static void main(String[] args) {
        // XADataSource
        // XAConnection
        // XAResource
        // Xid

        XATest xaTest = new XATest();

        // 分布式事务的数据一致性由支持XA协议的数据库来保证，只需要一个TC来协调XA的操作
        // TC的接口和RM的接口是互通的！！！

        try {
            XAConnection xaConnection = xaTest.getConnection();
            XAResource xaResource = xaConnection.getXAResource();

            Xid xid = xaTest.createXid("111", "111", 111);
            xaResource.start(xid, XAResource.TMNOFLAGS); // 开启XA事务
            // do business
            xaResource.end(xid, XAResource.TMSUCCESS); // XA事务结束
            xaResource.prepare(xid); // XA事务结束通知准备
            boolean onePhase = false;
            xaResource.commit(xid, onePhase); // XA事务提交
            xaResource.rollback(xid); // XA事务回滚
        } catch (SQLException | XAException e) {
            e.printStackTrace();
        }
    }

    public Xid createXid(String globalTransactionId, String branchQualifier, int formatId) {
        return new MysqlXid(
                // gtrid
                globalTransactionId.getBytes(StandardCharsets.UTF_8),
                // bqual
                branchQualifier.getBytes(StandardCharsets.UTF_8),
                formatId);
    }

    public XAConnection getConnection() {
        try {
            return getDataSource().getXAConnection();
        } catch (SQLException e) {
            throw new RuntimeException("Get XAConnection failed.", e);
        }
    }

    public XADataSource getDataSource() {
        if (dataSource == null) {
            synchronized (dataSource) {
                if (dataSource == null) {
                    dataSource = createXADataSource();
                }
            }
        }
        return dataSource;
    }

    private XADataSource createXADataSource() {
        Properties properties = new Properties();
        URL resource = XATest.class.getClassLoader().getResource("jdbc.properties");
        if (resource != null) {
            try (InputStream is = resource.openStream()) {
                properties.load(is);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        MysqlXADataSource dataSource = new MysqlXADataSource();
        dataSource.setUrl(properties.getProperty("jdbc.mysql.url"));
        dataSource.setUser(properties.getProperty("jdbc.mysql.username"));
        dataSource.setPassword(properties.getProperty("jdbc.mysql.password"));

        try {
            dataSource.setPinGlobalTxToPhysicalConnection(true);
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }

        return dataSource;
    }

    public void test() {
        XAConnection xaConnection = getConnection();
        xaConnection.addConnectionEventListener(new ConnectionEventListener() {
            @Override
            public void connectionClosed(ConnectionEvent event) {

            }

            @Override
            public void connectionErrorOccurred(ConnectionEvent event) {

            }
        });

        xaConnection.addStatementEventListener(new StatementEventListener() {
            @Override
            public void statementClosed(StatementEvent event) {

            }

            @Override
            public void statementErrorOccurred(StatementEvent event) {

            }
        });
    }
}
