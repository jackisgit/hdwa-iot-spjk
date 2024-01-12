package com.wanda.epc.callback;

import cn.hutool.core.util.RandomUtil;
import com.netsdk.lib.NetSDKLib;
import com.sun.jna.CallbackThreadInitializer;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PipedOutputStream;

/**
 * @author LianYanFei
 * @Title RealDataCallBack.java
 * @description 实时预览回调函数
 * @time 2023年11月28日 下午2:45:08
 **/
public class RealDataCallBack implements NetSDKLib.fRealDataCallBackEx2 {

    private final static Logger logger = LoggerFactory.getLogger(RealDataCallBack.class);


    private PipedOutputStream outputStream;// 管道输出流
    private PipedOutputStream picOutputStream;// 抓图管道流

    public boolean playbackcapture = false;// 开始抓图标志 true：开始抓图 false：结束抓图

    public RealDataCallBack(PipedOutputStream outputStream) {
        //这里是关键，默认回调是一个数据包产生一个线程，因为管道的机制，第一次写入和读取的线程会和管道绑定，如果线程G了管道也会关闭会出现 Write end dead \ Pipe closed错误，所以设置一个线程回调解决这个错误问题，如果不想设置这里可以用队列来解决，这里不详细阐述
        Native.setCallbackThreadInitializer(this, new CallbackThreadInitializer(true, false, "DahuaRealStream-" + RandomUtil.randomNumbers(8)));
        this.outputStream = outputStream;
    }

    public void setPicOutputStream(PipedOutputStream picOutputStream) {
        this.picOutputStream = picOutputStream;
    }


    @Override
    public void invoke(NetSDKLib.LLong lRealHandle, int dwDataType, Pointer pBuffer, int dwBufSize, NetSDKLib.LLong param, Pointer dwUser) {
        try {
            if (dwDataType == 5 || dwDataType == 1005) {
                outputStream.write(pBuffer.getByteArray(0, dwBufSize));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}
