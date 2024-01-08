package com.wanda.epc.sdk;

import com.wanda.epc.pojo.CameraPojo;
import com.wanda.epc.sdk.HCNetSDK.NET_DVR_DEVICEINFO_V30;
import com.sun.jna.NativeLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author LianYanFei
 * @Title HCLoginSDK.java
 * @description 注册/注销设备
 * @time 2023年11月28日 下午5:51:00
 **/
public class HCLoginSDK {

    private final static Logger logger = LoggerFactory.getLogger(HCLoginSDK.class);

    private NativeLong lUserID = new NativeLong(-1);// 注册接口的返回值
    private NET_DVR_DEVICEINFO_V30 lpDeviceinfo_V30;// 设备参数结构体
    private int errorcode = 0;// 错误码
    private boolean isLogin = false;// 登录状态
    private int count = 0;// 使用人数

    /**
     * @return boolean
     * @Title: login
     * @Description:注册
     **/
    public boolean login(CameraPojo pojo) {
        //注册
        HCNetSDK.NET_DVR_USER_LOGIN_INFO m_strLoginInfo = new HCNetSDK.NET_DVR_USER_LOGIN_INFO();//设备登录信息
        HCNetSDK.NET_DVR_DEVICEINFO_V40 m_strDeviceInfo = new HCNetSDK.NET_DVR_DEVICEINFO_V40();//设备信息

        String m_sDeviceIP = pojo.getIp();//设备ip地址
        m_strLoginInfo.sDeviceAddress = new byte[HCNetSDK.NET_DVR_DEV_ADDRESS_MAX_LEN];
        System.arraycopy(m_sDeviceIP.getBytes(), 0, m_strLoginInfo.sDeviceAddress, 0, m_sDeviceIP.length());

        String m_sUsername = pojo.getUsername();//设备用户名
        m_strLoginInfo.sUserName = new byte[HCNetSDK.NET_DVR_LOGIN_USERNAME_MAX_LEN];
        System.arraycopy(m_sUsername.getBytes(), 0, m_strLoginInfo.sUserName, 0, m_sUsername.length());

        String m_sPassword = pojo.getPassword();//设备密码
        m_strLoginInfo.sPassword = new byte[HCNetSDK.NET_DVR_LOGIN_PASSWD_MAX_LEN];
        System.arraycopy(m_sPassword.getBytes(), 0, m_strLoginInfo.sPassword, 0, m_sPassword.length());

        m_strLoginInfo.wPort = Short.valueOf(pojo.getPort());
        m_strLoginInfo.bUseAsynLogin = 0; //是否异步登录：0- 否，1- 是
        m_strLoginInfo.write();


        lUserID = HCNetSDK.INSTANCE.NET_DVR_Login_V40(m_strLoginInfo, m_strDeviceInfo);
        if (this.lUserID.intValue() == -1) {
            this.isLogin = false;
            this.errorcode = HCNetSDK.INSTANCE.NET_DVR_GetLastError();// 得到错误码
            return false;
        } else {
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
        if (HCNetSDK.INSTANCE.NET_DVR_Logout(lUserID)) {
            this.isLogin = false;
            return true;
        } else {
            this.errorcode = HCNetSDK.INSTANCE.NET_DVR_GetLastError();
            logger.error("hcsdk 注销设备出错,错误码:" + errorcode);
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
    public NativeLong getLUserID() {
        return this.lUserID;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

}
