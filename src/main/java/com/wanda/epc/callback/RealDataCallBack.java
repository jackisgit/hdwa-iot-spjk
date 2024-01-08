package com.wanda.epc.callback;

import com.netsdk.lib.NetSDKLib;
import com.sun.jna.Pointer;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.PipedOutputStream;

/**
 * @author LianYanFei
 * @version 1.0
 * @project iot_epc_spdj
 * @description 实时预览回调函数
 * @date 2023/10/18 16:26:28
 */
@Slf4j
public class RealDataCallBack implements NetSDKLib.fRealDataCallBackEx {

    private PipedOutputStream outputStream;// 管道输出流

    public RealDataCallBack(PipedOutputStream outputStream) {
        this.outputStream = outputStream;
    }


    @Override
    public void invoke(NetSDKLib.LLong lRealHandle, int dwDataType, Pointer pBuffer, int dwBufSize, int param, Pointer dwUser) {
        try {

            log.info("----流类型：{},数据大小：{}-----",dwDataType,pBuffer.getByteArray(0, dwBufSize).length);
            outputStream.write(pBuffer.getByteArray(0, dwBufSize));
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }
}
