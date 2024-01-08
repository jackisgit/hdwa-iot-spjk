package com.wanda.epc.callback;

import cn.hutool.log.Log;
import com.wanda.epc.play.HlsPush;
import com.wanda.epc.sdk.HCNetSDK.FPlayDataCallBack;
import com.sun.jna.NativeLong;
import com.sun.jna.ptr.ByteByReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PipedOutputStream;

/**
 * @Title PlayDataCallBack.java
 * @description 历史回放回调函数
 * @time 2023年11月28日 下午2:44:51
 * @author LianYanFei
 **/
public class PlayDataCallBack implements FPlayDataCallBack {
	private final static Logger logger = LoggerFactory.getLogger(PlayDataCallBack.class);
	private PipedOutputStream outputStream;// 管道输出流
	private PipedOutputStream picOutputStream;// 抓图管道流

	public boolean playbackcapture = false;// 开始抓图标志 true：开始抓图 false：结束抓图

	public PlayDataCallBack(PipedOutputStream outputStream) {
		this.outputStream = outputStream;
	}

	public void setPicOutputStream(PipedOutputStream picOutputStream) {
		this.picOutputStream = picOutputStream;
	}

	@Override
	public void invoke(NativeLong lPlayHandle, int dwDataType, ByteByReference pBuffer, int dwBufSize, int dwUser) {
		try {
			if (playbackcapture) {
				// 将数据同时写入抓图管道流中
				picOutputStream.write(pBuffer.getPointer().getByteArray(0, dwBufSize));
			}

			outputStream.write(pBuffer.getPointer().getByteArray(0, dwBufSize));
		} catch (Exception e) {
//			logger.error(e.getMessage());
		}
	}

}
