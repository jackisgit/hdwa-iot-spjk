package com.wanda.epc.sdk;

import com.netsdk.lib.NetSDKLib;
import com.netsdk.lib.ToolKits;
import com.sun.jna.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * @author LianYanFei
 * @Title HCInitSDK.java
 * @description 初始化/注销sdk
 * @time 2023年11月28日 下午5:20:18
 **/
@Component
public class DHInitSDK {

    private final static Logger logger = LoggerFactory.getLogger(DHInitSDK.class);


    public static NetSDKLib netSdk = NetSDKLib.NETSDK_INSTANCE;

    private int errorCode = 0;// 错误码

    boolean isInit = false;// 初始化状态

    // 设备断线回调
    private static DisConnect disConnectCallback = new DisConnect();
    // 设备重连通知回调
    private static HaveReConnect haveReConnect = new HaveReConnect();
    // 抓图回调

    /**
     * @return boolean
     * @Title: init
     * @Description:初始化sdk
     **/
    public boolean init() {
        boolean bInit = netSdk.CLIENT_Init(disConnectCallback, null);
        if (!bInit) {
            this.errorCode = netSdk.CLIENT_GetLastError();
            return false;
        } else {
            //打开日志，可选
            NetSDKLib.LOG_SET_PRINT_INFO setLog = new NetSDKLib.LOG_SET_PRINT_INFO();
            File path = new File("./sdklog/");
            if (!path.exists()) {
                path.mkdir();
            }
            String logPath = path.getAbsoluteFile().getParent() + "\\sdklog\\" + System.currentTimeMillis() + ".log";
            setLog.nPrintStrategy = 0;
            setLog.bSetFilePath = 1;
            System.arraycopy(logPath.getBytes(), 0, setLog.szLogFilePath, 0, logPath.getBytes().length);
            System.out.println(logPath);
            setLog.bSetPrintStrategy = 1;
            boolean bLogopen = netSdk.CLIENT_LogOpen(setLog);
            if (!bLogopen) {
                logger.error("打开日志失败。。。");
            }
            // 设置断线重连回调接口，设置过断线重连成功回调函数后，当设备出现断线情况，SDK内部会自动进行重连操作
            // 此操作为可选操作，但建议用户进行设置
            netSdk.CLIENT_SetAutoReconnect(haveReConnect, null);
            //设置登录超时时间和尝试次数，可选
            int waitTime = 5000; //登录请求响应超时时间设置为5S
            int tryTimes = 1;    //登录时尝试建立链接1次
            netSdk.CLIENT_SetConnectTime(waitTime, tryTimes);
            // 设置更多网络参数，NET_PARAM的nWaittime，nConnectTryNum成员与CLIENT_SetConnectTime
            // 接口设置的登录设备超时时间和尝试次数意义相同,可选
            NetSDKLib.NET_PARAM netParam = new NetSDKLib.NET_PARAM();
            netParam.nConnectTime = 10000;      // 登录时尝试建立链接的超时时间
            netParam.nGetConnInfoTime = 3000;   // 设置子连接的超时时间
            netParam.nGetDevInfoTime = 3000;//获取设备信息超时时间，为0默认1000ms
            netSdk.CLIENT_SetNetworkParam(netParam);
            this.isInit = true;
            return true;
        }
    }

    /**
     * @return isInit
     * @Title: cleanup
     * @Description:注销sdk
     **/
    public boolean cleanup() {
        if (! netSdk.CLIENT_LogClose()) {
            logger.info("注销sdk失败：{}",ToolKits.getErrorCode());
            this.errorCode =netSdk.CLIENT_GetLastError();
            return false;
        } else {
            this.isInit = false;
            return true;
        }
    }

    /**
     * @return errorCode
     * @Title: getErrorCode
     * @Description:获取错误码
     **/
    public int getErrorCode() {
        return this.errorCode;
    }

    /**
     * @return isInit
     * @Title: getIsInit
     * @Description:获取初始化状态
     **/
    public boolean getIsInit() {
        return this.isInit;
    }


    /**
     * 设备断线回调: 通过 CLIENT_Init 设置该回调函数，当设备出现断线时，SDK会调用该函数
     */
    private static class DisConnect implements NetSDKLib.fDisConnect {
        public void invoke(NetSDKLib.LLong m_hLoginHandle, String pchDVRIP, int nDVRPort, Pointer dwUser) {
            System.out.printf("Device[%s] Port[%d] DisConnect!\n", pchDVRIP, nDVRPort);
        }
    }

    /**
     * 设备重连回调: 通过 CLIENT_Init 设置该回调函数，当设备出现断线时，SDK会调用该函数
     */
    private static class HaveReConnect implements NetSDKLib.fHaveReConnect {
        public void invoke(NetSDKLib.LLong m_hLoginHandle, String pchDVRIP, int nDVRPort, Pointer dwUser) {
            System.out.printf("ReConnect Device[%s] Port[%d]\n", pchDVRIP, nDVRPort);
        }
    }

}
