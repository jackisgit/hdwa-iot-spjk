package com.wanda.epc.callback;

import com.netsdk.lib.NetSDKLib;
import com.sun.jna.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PipedOutputStream;

/**
 * @author LianYanFei
 * @Title PlayDataCallBack.java
 * @description 历史回放回调函数
 * @time 2023年11月28日 下午2:44:51
 **/
public class PlayDataCallBack implements NetSDKLib.fDataCallBack {

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
    public int invoke(NetSDKLib.LLong lRealHandle, int dwDataType, Pointer pBuffer, int dwBufSize, Pointer dwUser) {
        try {
            if (dwDataType == (NetSDKLib.NET_DATA_CALL_BACK_VALUE + NetSDKLib.EM_REAL_DATA_TYPE.EM_REAL_DATA_TYPE_FLV_STREAM)) {
                outputStream.write(pBuffer.getByteArray(0, dwBufSize));
            }
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
        return 0;
    }

}
