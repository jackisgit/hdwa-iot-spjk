package com.wanda.epc.callback;

import com.wanda.epc.sdk.HCNetSDK;
import com.wanda.epc.sdk.HCNetSDK.FRealDataCallBack_V30;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.ByteByReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PipedOutputStream;

/**
 * @Title RealDataCallBack.java
 * @description 实时预览回调函数
 * @time 2023年11月28日 下午2:45:08
 * @author LianYanFei
 **/
public class RealDataCallBack implements FRealDataCallBack_V30 {

	private final static Logger logger = LoggerFactory.getLogger(RealDataCallBack.class);

	private PipedOutputStream outputStream;// 管道输出流
	private PipedOutputStream picOutputStream;// 抓图管道流

	public boolean playbackcapture = false;// 开始抓图标志 true：开始抓图 false：结束抓图

	public RealDataCallBack(PipedOutputStream outputStream) {
		this.outputStream = outputStream;
	}

	public void setPicOutputStream(PipedOutputStream picOutputStream) {
		this.picOutputStream = picOutputStream;
	}

	@Override
	public void invoke(NativeLong lRealHandle, int dwDataType, ByteByReference pBuffer, int dwBufSize, Pointer pUser) {
		if ( dwDataType == HCNetSDK.NET_DVR_STREAMDATA) {
			try {
				if (playbackcapture) {
					// 将数据同时写入抓图管道流中
					picOutputStream.write(pBuffer.getPointer().getByteArray(0, dwBufSize));
				}
				outputStream.write(pBuffer.getPointer().getByteArray(0, dwBufSize));
			} catch (IOException e) {
			logger.error(e.getMessage());
			}
		}
	}
}
