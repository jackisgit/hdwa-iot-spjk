package com.wanda.epc.sdk;

import org.springframework.stereotype.Component;

/**
 * @Title HCInitSDK.java
 * @description 初始化/注销sdk
 * @time 2023年11月28日 下午5:20:18
 * @author LianYanFei
 **/
@Component
public class HCInitSDK {

	private int errorCode = 0;// 错误码

	boolean isInit = false;// 初始化状态

	/**
	 * @Title: init
	 * @Description:初始化sdk
	 * @return boolean
	 **/
	public boolean init() {
		if (!HCNetSDK.INSTANCE.NET_DVR_Init()) {
			this.errorCode = HCNetSDK.INSTANCE.NET_DVR_GetLastError();
			return false;
		} else {
			this.isInit = true;
			return true;
		}
	}

	/**
	 * @Title: cleanup
	 * @Description:注销sdk
	 * @return isInit
	 **/
	public boolean cleanup() {
		if (!HCNetSDK.INSTANCE.NET_DVR_Cleanup()) {
			this.errorCode = HCNetSDK.INSTANCE.NET_DVR_GetLastError();
			return false;
		} else {
			this.isInit = false;
			return true;
		}
	}

	/**
	 * @Title: getErrorCode
	 * @Description:获取错误码
	 * @return errorCode
	 **/
	public int getErrorCode() {
		return this.errorCode;
	}

	/**
	 * @Title: getIsInit
	 * @Description:获取初始化状态
	 * @return isInit
	 **/
	public boolean getIsInit() {
		return this.isInit;
	}
}
