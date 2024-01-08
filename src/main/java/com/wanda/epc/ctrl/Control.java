package com.wanda.epc.ctrl;

import com.alibaba.fastjson.JSON;
import com.sun.jna.NativeLong;
import com.wanda.epc.sdk.HCNetSDK;
import lombok.extern.slf4j.Slf4j;

/**
 * 摄像头控制
 * @author ZJ
 *
 */
@Slf4j
public class Control {

	
	/**
	 * 云台控制<br/>
	 * 云台控制的方式为调用该方法摄像头便会一直执行该操作,直到该操作接收到"停止"指令及(iStop)参数
	 * 
	 * @param ip 摄像头ip
	 * @param iCommand 控制指令
	 * @param iSpeed 云台运行速度 
	 * @param iStop 是否为停止操作
	 * @return
	 */
	public static boolean cloudControl(String ip, CloudCode iCommand, CloudCode iSpeed, Integer iStop,NativeLong lRealHandle) {
		log.info("开始调用云台控制,ip:{},iCommand:{},iSpeed:{},istop:{}",ip,JSON.toJSONString(iCommand),JSON.toJSON(iSpeed),iStop);

		//判断是否为停止操作
		if (iSpeed.getKey() == 1) {
			boolean ptzControl = HCNetSDK.INSTANCE.NET_DVR_PTZControl(lRealHandle, iCommand.getKey(), iStop);
			log.info("云台控制ptzControl返回结果集：{}",ptzControl);
		}

		boolean withSpeed = HCNetSDK.INSTANCE.NET_DVR_PTZControlWithSpeed(lRealHandle, iCommand.getKey(), iStop, iSpeed.getKey());
		log.info("云台控制withSpeed返回结果集：{}",withSpeed);
		return withSpeed;
	}
}