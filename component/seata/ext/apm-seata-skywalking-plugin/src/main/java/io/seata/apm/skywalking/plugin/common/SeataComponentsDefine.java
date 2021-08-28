package io.seata.apm.skywalking.plugin.common;

import org.apache.skywalking.apm.network.trace.component.OfficialComponent;

/**
 * @author baB_hyf
 * @date 2021/08/28
 */
public class SeataComponentsDefine {
    public static final OfficialComponent SEATA = new OfficialComponent(108, "Seata");
}
