package com.wanda.epc.sdk;

import com.wanda.epc.pojo.CameraPojo;
import com.wanda.epc.sdk.HCNetSDK.NET_DVR_DEVICEINFO_V30;
import com.sun.jna.NativeLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Title HCLoginSDK.java
 * @description 注册/注销设备
 * @time 2023年11月28日 下午5:51:00
 * @author LianYanFei
 **/
public class HCLoginSDK {

	private final static Logger logger = LoggerFactory.getLogger(HCLoginSDK.class);

	private NativeLong lUserID = new NativeLong(-1);// 注册接口的返回值
	private NET_DVR_DEVICEINFO_V30 lpDeviceinfo_V30;// 设备参数结构体
	private int errorcode = 0;// 错误码
	private boolean isLogin = false;// 登录状态
	private int count = 0;// 使用人数

	/**
	 * @Title: login
	 * @Description:注册
	 * @return boolean
	 **/
	public boolean login(CameraPojo pojo) {
		this.lUserID = HCNetSDK.INSTANCE.NET_DVR_Login_V30(pojo.getIp(), Short.parseShort(pojo.getPort()),
				pojo.getUsername(), pojo.getPassword(), this.lpDeviceinfo_V30);
		if (this.lUserID.longValue() > -1) {
			this.isLogin = true;
			return true;
		} else {
			this.isLogin = false;
			this.errorcode = HCNetSDK.INSTANCE.NET_DVR_GetLastError();// 得到错误码
			return false;
		}
	}

	/**
	 * @Title: logout
	 * @Description:注销
	 * @return boolean
	 **/
	public boolean logout() {
		if (HCNetSDK.INSTANCE.NET_DVR_Logout(this.lUserID)) {
			this.isLogin = false;
			return true;
		} else {
			this.errorcode = HCNetSDK.INSTANCE.NET_DVR_GetLastError();
			logger.error("hcsdk 注销设备出错,错误码:" + errorcode);
			return false;
		}
	}

	/**
	 * @Title: getErrorcode
	 * @Description:获取错误码
	 * @return errorcode
	 **/
	public int getErrorCode() {
		return this.errorcode;
	}

	/**
	 * @Title: getIsLogin
	 * @Description:获取注册状态
	 * @return boolean
	 **/
	public boolean getIsLogin() {
		return this.isLogin;
	}

	/**
	 * @Title: getLUserID
	 * @Description:获取lUserID
	 * @return lUserID
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
