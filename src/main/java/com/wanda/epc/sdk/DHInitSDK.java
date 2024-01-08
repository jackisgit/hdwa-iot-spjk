package com.wanda.epc.sdk;

import com.netsdk.lib.NetSDKLib;
import com.sun.jna.Pointer;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author LianYanFei
 * @version 1.0
 * @project iot_epc_spdj
 * @description 大华初始化sdk
 * @date 2023/10/18 18:04:24
 */
@Slf4j
@Component
public class DHInitSDK {

    private int errorCode = 0;// 错误码

    boolean isInit = false;// 初始化状态

    private DisConnect disConnect = new DisConnect();  // 设备断线通知回调


    /**
     * @return boolean
     * @Title: init
     * @Description:初始化sdk
     **/
    public boolean init() {
        if (!NetSDKLib.NETSDK_INSTANCE.CLIENT_Init(disConnect, null)) {
            this.errorCode = NetSDKLib.NETSDK_INSTANCE.CLIENT_GetLastError();
            return false;
        } else {
            log.info("--------初始化成功---------");
            this.isInit = true;
            return true;
        }
    }

    /**
     * @return isInit
     * @Title: cleanup
     * @Description:注销sdk
     **/
    public void cleanup() {
        NetSDKLib.NETSDK_INSTANCE.CLIENT_Cleanup();
    }

    public int getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    public boolean isInit() {
        return isInit;
    }

    public void setInit(boolean init) {
        isInit = init;
    }

    public class DisConnect implements NetSDKLib.fDisConnect {
        public void invoke(NetSDKLib.LLong lLoginID, String pchDVRIP, int nDVRPort, Pointer dwUser) {
            log.info("设备断线重连接：{},{}", pchDVRIP, nDVRPort);
        }
    }

}
