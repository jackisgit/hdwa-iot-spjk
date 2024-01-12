package com.wanda.epc.sdk;

import com.netsdk.lib.NetSDKLib;
import com.netsdk.lib.ToolKits;
import com.sun.jna.ptr.IntByReference;
import com.wanda.epc.cache.CacheUtil;
import com.wanda.epc.pojo.CameraPojo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author LianYanFei
 * @Title HCLoginSDK.java
 * @description 注册/注销设备
 * @time 2023年11月28日 下午5:51:00
 **/
public class DHLoginSDK {

    private final static Logger logger = LoggerFactory.getLogger(DHLoginSDK.class);

    public static NetSDKLib netSdk = NetSDKLib.NETSDK_INSTANCE;


    public static NetSDKLib.LLong m_hLoginHandle = new NetSDKLib.LLong(0);// 注册接口的返回值
    private int errorcode = 0;// 错误码
    private boolean isLogin = false;// 登录状态
    private int count = 0;// 使用人数

    public boolean login(CameraPojo pojo) {
        NetSDKLib.NET_DEVICEINFO_Ex info = new NetSDKLib.NET_DEVICEINFO_Ex();
        m_hLoginHandle = netSdk.CLIENT_LoginEx2(pojo.getIp(), Integer.parseInt(pojo.getPort()), pojo.getUsername(), pojo.getPassword(), 0, null, info, new IntByReference(0));
        if (m_hLoginHandle.longValue() == 0) {
            System.err.printf("登录失败！", pojo.getIp(), pojo.getPort(), ToolKits.getErrorCode());
            logger.info("设备登录失败：{}", ToolKits.getErrorCode());
            this.isLogin = false;
            this.errorcode = netSdk.CLIENT_GetLastError();
            return false;
        } else {
            System.out.println("登录成功： [ " + pojo.getIp() + " ]");
            this.isLogin = true;
            return true;
        }
    }

    /**
     * @return boolean
     * @Title: logout
     * @Description:注销
     **/
    public boolean logout() {
        if (netSdk.CLIENT_Logout(this.m_hLoginHandle)) {
            this.isLogin = false;
            return true;
        } else {
            this.errorcode = netSdk.CLIENT_GetLastError();
            logger.error("dhsdk 注销设备出错,错误码:" + errorcode);
            return false;
        }


    }

    public boolean logoutBack(NetSDKLib.LLong playbackLoginHandle) {
        if (netSdk.CLIENT_Logout(this.m_hLoginHandle)) {
            this.isLogin = false;
            return true;
        } else {
            this.errorcode = netSdk.CLIENT_GetLastError();
            logger.error("dhsdk 注销设备出错,错误码:" + errorcode);
            return false;
        }


    }

    /**
     * @return errorcode
     * @Title: getErrorcode
     * @Description:获取错误码
     **/
    public int getErrorCode() {
        return this.errorcode;
    }

    /**
     * @return boolean
     * @Title: getIsLogin
     * @Description:获取注册状态
     **/
    public boolean getIsLogin() {
        return this.isLogin;
    }

    /**
     * @return lUserID
     * @Title: getLUserID
     * @Description:获取lUserID
     **/
    public NetSDKLib.LLong getLUserID() {
        return this.m_hLoginHandle;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

}
