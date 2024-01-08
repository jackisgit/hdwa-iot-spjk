package com.wanda.epc.sdk;

import com.netsdk.lib.NetSDKLib;
import com.sun.jna.ptr.IntByReference;
import com.wanda.epc.pojo.CameraPojo;
import lombok.extern.slf4j.Slf4j;

/**
 * @author LianYanFei
 * @version 1.0
 * @project iot_epc_spdj
 * @description 大华登录/注销
 * @date 2023/10/18 17:08:39
 */

@Slf4j
public class DHLoginSDK {

    NetSDKLib NetSdk = NetSDKLib.NETSDK_INSTANCE;

    private   NetSDKLib.LLong loginHandler; //登录句柄

    private int errorCode;// 错误码
    private boolean isLogin = false;// 登录状态

    private int count = 0;// 使用人数


    /**
     *@description 设备登录
     *@author LianYanFei
     *@date 2023/10/18
     */
    public boolean login(CameraPojo cameraPojo){
        NetSDKLib.NET_DEVICEINFO_Ex info = new NetSDKLib.NET_DEVICEINFO_Ex();
        loginHandler = NetSdk.CLIENT_LoginEx2(cameraPojo.getIp(), Integer.parseInt(cameraPojo.getPort()), cameraPojo.getUsername(), cameraPojo.getPassword(), 0, null, info, new IntByReference(0));
        if (this.loginHandler.longValue() > -1) {
            this.isLogin = true;
            return true;
        } else {
            this.isLogin = false;
            this.errorCode =  NetSDKLib.NETSDK_INSTANCE.CLIENT_GetLastError();// 得到错误码
            return false;
        }
    }

    /**
     *@description 设备登出
     *@author LianYanFei
     *@date 2023/10/18
     */
    public boolean logout(){
        return NetSdk.CLIENT_Logout(loginHandler);
    }

    public int getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    public boolean getIsLogin() {
        return this.isLogin;
    }

    public void setLogin(boolean login) {
        isLogin = login;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public NetSDKLib.LLong getLoginHandler() {
        return loginHandler;
    }

    public void setLoginHandler(NetSDKLib.LLong loginHandler) {
        this.loginHandler = loginHandler;
    }
}
