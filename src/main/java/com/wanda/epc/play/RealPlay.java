package com.wanda.epc.play;

import com.netsdk.lib.NetSDKLib;
import com.wanda.epc.cache.CacheUtil;
import com.wanda.epc.callback.RealDataCallBack;
import com.wanda.epc.config.Config;
import com.wanda.epc.pojo.CameraPojo;
import com.wanda.epc.sdk.DHLoginSDK;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

/**
 * @author LianYanFei
 * @version 1.0
 * @project iot_epc_spdj
 * @description SDK预览/回放
 * @date 2023/10/18 18:13:06
 */
@Slf4j
public class RealPlay {

    private static Config config;

    // 通过applicationContext上下文获取Config类
    public static void setApplicationContext(ApplicationContext applicationContext) {
        config = applicationContext.getBean(Config.class);
    }

    private DHLoginSDK login;
    private CameraPojo cameraPojo;// 设备信息
    private int errorcode = 0;// 错误码
    private RealDataCallBack realDataCallBack;// 预览回调函数
    private NetSDKLib.LLong lRealPlayHandle;// 实时预览播放句柄
    private PipedInputStream inputStream;// 管道输入流
    private PipedOutputStream outputStream;// 管道输出流

    private int playSign;// 直播和历史回放标志;0:直播,1:历史回放

    public RealPlay(CameraPojo cameraPojo, DHLoginSDK login) {
        this.cameraPojo = cameraPojo;
        this.login = login;
    }


    public void play() {
        inputStream = new PipedInputStream();
        outputStream = new PipedOutputStream();
        try {
            // 管道流建立连接
            inputStream.connect(outputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
        boolean isPlay = playSdk();
        if (isPlay) {
            // 直播
            if (playSign == 0) {
                log.info("-----开始预览推流----------");
                RtmpPush push = new RtmpPush(cameraPojo, inputStream, outputStream, lRealPlayHandle, playSign);
                CacheUtil.PUSHRTMPMAP.put(cameraPojo.getToken(), push);
                push.push();
                if (CacheUtil.PUSHRTMPMAP.get(cameraPojo.getToken()).getExitcode() == 0
                        || CacheUtil.PUSHRTMPMAP.get(cameraPojo.getToken()).getExitcode() == 1) {
                    CacheUtil.PUSHRTMPMAP.remove(cameraPojo.getToken());
                    return;
                }
                if (CacheUtil.PUSHRTMPMAP.get(cameraPojo.getToken()).getExitcode() == 2) {
                    CacheUtil.PUSHRTMPMAP.remove(cameraPojo.getToken());
                    play();
                }
            }
        }
    }

    public boolean playSdk() {

        if (null == cameraPojo.getStarttime()) {
            playSign = 0;// 直播标志
            // 直播
            realDataCallBack = new RealDataCallBack(outputStream);
            // 实时预览
            NetSDKLib.NET_IN_REALPLAY_BY_DATA_TYPE netInRealplayByDataType = new NetSDKLib.NET_IN_REALPLAY_BY_DATA_TYPE();
            log.info("--------通道号--------------：{}", cameraPojo.getChannel());
            netInRealplayByDataType.nChannelID = Integer.parseInt(cameraPojo.getChannel());
            netInRealplayByDataType.rType = 0;
            netInRealplayByDataType.emDataType = 1;
            netInRealplayByDataType.cbRealData = new RealDataCallBack(outputStream);
            NetSDKLib.NET_OUT_REALPLAY_BY_DATA_TYPE netOutRealplayByDataType = new NetSDKLib.NET_OUT_REALPLAY_BY_DATA_TYPE();
            lRealPlayHandle = NetSDKLib.NETSDK_INSTANCE.CLIENT_RealPlayByDataType(login.getLoginHandler(), netInRealplayByDataType, netOutRealplayByDataType, 500);
            log.info("----------预览句柄结果----------:{}", lRealPlayHandle.longValue());
            errorcode = NetSDKLib.NETSDK_INSTANCE.CLIENT_GetLastError();
            if (lRealPlayHandle.longValue() < 0 && errorcode != 0) {
                log.error("hcsdk 实时预览失败,错误码：" + errorcode + " 设备信息：[ip:" + cameraPojo.getIp() + " port:"
                        + cameraPojo.getPort() + " channel:" + cameraPojo.getChannel());
                return false;
            } else {
                // 将callBack保存在缓存中
                CacheUtil.LIVECALLBACK.put(cameraPojo.getToken(), realDataCallBack);

                log.info("hcsdk 实时预览成功  设备信息：[ip:" + cameraPojo.getIp() + " port:" + cameraPojo.getPort()
                        + " channel:" + cameraPojo.getChannel());
                return true;
            }
        }
        return false;

    }
}
