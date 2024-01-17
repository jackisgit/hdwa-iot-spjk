package com.wanda.epc.play;

import com.alibaba.fastjson.JSON;
import com.netsdk.lib.NetSDKLib;
import com.netsdk.lib.ToolKits;
import com.netsdk.lib.enumeration.EM_AUDIO_DATA_TYPE;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.wanda.epc.cache.CacheUtil;
import com.wanda.epc.callback.PlayDataCallBack;
import com.wanda.epc.callback.RealDataCallBack;
import com.wanda.epc.config.Config;
import com.wanda.epc.controller.CameraController;
import com.wanda.epc.pojo.CameraPojo;
import com.wanda.epc.sdk.DHLoginSDK;
import com.sun.jna.NativeLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;;

/**
 * @author LianYanFei
 * @Title RealPlay.java
 * @description SDK预览/回放
 * @time 2023年11月28日 上午11:43:07
 **/
public class RealPlay {

    private final static Logger logger = LoggerFactory.getLogger(RealPlay.class);

    // 配置类
    private static Config config;

    // 通过applicationContext上下文获取Config类
    public static void setApplicationContext(ApplicationContext applicationContext) {
        config = applicationContext.getBean(Config.class);
    }

    private DHLoginSDK login;
    private CameraPojo cameraPojo;// 设备信息
    private int errorcode = 0;// 错误码
    private RealDataCallBack realDataCallBack;// 预览回调函数
    private PlayDataCallBack fPlayDataCallBack;// 历史回放回调函数
    private NetSDKLib.LLong lRealPlayHandle;// 实时预览播放句柄
    private NetSDKLib.LLong lHisPlayHandle;// 历史回放播放句柄
    private int playSign;// 直播和历史回放标志;0:直播,1:历史回放
    private PipedInputStream inputStream;// 管道输入流
    private PipedOutputStream outputStream;// 管道输出流

    public RealPlay(CameraPojo cameraPojo, DHLoginSDK login) {
        this.cameraPojo = cameraPojo;
        this.login = login;
    }

    /**
     * @return void
     * @Title: play
     * @Description:开始推流
     **/
    public void play() {
        inputStream = new PipedInputStream();
        outputStream = new PipedOutputStream();
        try {
            // 管道流建立连接
            inputStream.connect(outputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }

        boolean isPlay = playsdk();

        if (isPlay) {
            // 直播
            if (playSign == 0) {
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
            } else {
                // 历史 rtmp&hls
                if (null != cameraPojo.getRtmp() && !"".equals(cameraPojo.getRtmp())) {
                    RtmpPush push = new RtmpPush(cameraPojo, inputStream, outputStream, lHisPlayHandle, playSign);
                    CacheUtil.PUSHRTMPMAP.put(cameraPojo.getToken(), push);
                    if (cameraPojo.getReHistory()) {
                        push.setDts(CacheUtil.DTSMAP.get(cameraPojo.getToken()));
                    }
                    push.push();
                    CacheUtil.PUSHRTMPMAP.remove(cameraPojo.getToken());
                } else if (null != cameraPojo.getM3u8path() && !"".equals(cameraPojo.getM3u8path())) {
                    HlsPush push = new HlsPush(cameraPojo, inputStream, outputStream, lHisPlayHandle, playSign);
                    CacheUtil.PUSHHLSMAP.put(cameraPojo.getToken(), push);
                    push.push();
                    CacheUtil.PUSHHLSMAP.remove(cameraPojo.getToken());
                }
            }
        }
    }

    /**
     * @return boolean
     * @Title: playsdk
     * @Description:sdk接口调用
     **/
    public boolean playsdk() {
        if (null == cameraPojo.getStarttime()) {
            playSign = 0;// 直播标志
            // 直播
            realDataCallBack = new RealDataCallBack(outputStream);
            // 实时预览
            NetSDKLib.NET_IN_REALPLAY_BY_DATA_TYPE inParam = new NetSDKLib.NET_IN_REALPLAY_BY_DATA_TYPE();
            inParam.rType = 0;// 实时预览-主码流 ,等同于NET_RType_Realplay
            inParam.emDataType = NetSDKLib.EM_REAL_DATA_TYPE.EM_REAL_DATA_TYPE_FLV_STREAM;
            inParam.nChannelID = Integer.parseInt(cameraPojo.getChannel());
            inParam.emAudioType = EM_AUDIO_DATA_TYPE.EM_AUDIO_DATA_TYPE_AAC.ordinal();
            inParam.cbRealDataEx = realDataCallBack;
            inParam.dwUser = null;
            //返回对象
            NetSDKLib.NET_OUT_REALPLAY_BY_DATA_TYPE stOut = new NetSDKLib.NET_OUT_REALPLAY_BY_DATA_TYPE();
            logger.info("实时预览用户句柄：{}", login.getLUserID().intValue());
            NetSDKLib.LLong lUserID = login.getLUserID();
            lRealPlayHandle = NetSDKLib.NETSDK_INSTANCE.CLIENT_RealPlayByDataType(lUserID, inParam, stOut, 5000);
            errorcode = NetSDKLib.NETSDK_INSTANCE.CLIENT_GetLastError();
            logger.info("实时预览句柄：{},错误状态码：{}", lRealPlayHandle.intValue(), ToolKits.getErrorCode());
            if (lRealPlayHandle.intValue() != 0) {
                // 将callBack保存在缓存中
                CacheUtil.LIVECALLBACK.put(cameraPojo.getToken(), realDataCallBack);
                //保存预览时登录句柄后期用于云台控制
                logger.info("dahuasdk 实时预览成功  设备信息：[ip:" + cameraPojo.getIp() + " port:" + cameraPojo.getPort()
                        + " channel:" + cameraPojo.getChannel() + "]");
                return true;
            } else {
                logger.info(ToolKits.getErrorCode());
                logger.error("dahuasdk 实时预览失败,错误码：" + errorcode + " 设备信息：[ip:" + cameraPojo.getIp() + " port:"
                        + cameraPojo.getPort() + " channel:" + cameraPojo.getChannel() + "]");
                return false;

            }
        } else {
            playSign = 1;// 历史回放标志
            fPlayDataCallBack = new PlayDataCallBack(outputStream);
            NetSDKLib.NET_IN_PLAYBACK_BY_DATA_TYPE stuIn = new NetSDKLib.NET_IN_PLAYBACK_BY_DATA_TYPE();
            String[] begin = cameraPojo.getStarttime().split(" ");
            NetSDKLib.NET_TIME start_time = handleDate(begin[0], begin[1]);
            String[] end = cameraPojo.getEndtime().split(" ");
            NetSDKLib.NET_TIME end_time = handleDate(end[0], end[1]);
            stuIn.stStartTime = start_time;
            stuIn.stStopTime = end_time;
            stuIn.hWnd = null;        // 播放窗格
            stuIn.dwPosUser = null;
            stuIn.nChannelID = Integer.parseInt(cameraPojo.getChannel());
            stuIn.fDownLoadDataCallBack = fPlayDataCallBack;
            stuIn.dwDataUser = null;
            stuIn.emDataType = NetSDKLib.EM_REAL_DATA_TYPE.EM_REAL_DATA_TYPE_FLV_STREAM;
            stuIn.nPlayDirection = 0;                            // 正放
            DHLoginSDK loginSDK = CacheUtil.PLAY_BACK_LOGIN_MODULE.get(cameraPojo.getIp());
            NetSDKLib.NET_OUT_PLAYBACK_BY_DATA_TYPE stuOut = new NetSDKLib.NET_OUT_PLAYBACK_BY_DATA_TYPE();
            lHisPlayHandle = NetSDKLib.NETSDK_INSTANCE.CLIENT_PlayBackByDataType(loginSDK.getLUserID(), stuIn, stuOut, 5000);
            if (lHisPlayHandle.longValue() != 0) {
                // 保存回放句柄
                cameraPojo.setlHisPlayHandle(lHisPlayHandle);
                // 将callBack保存在缓存中
                CacheUtil.HISTORYCALLBACK.put(cameraPojo.getToken(), fPlayDataCallBack);
                // 保存回放句柄
                CacheUtil.PLAY_BACK_PLAY_HANDLE.put(cameraPojo.getIp(), lHisPlayHandle);
                logger.info("dhsdk 按时间回放录像文件成功" + " 设备信息：[ip:" + cameraPojo.getIp() + " port:"
                        + cameraPojo.getPort() + " channel:" + cameraPojo.getChannel() + " statrtime:" + cameraPojo.getStarttime() + " endtime:"
                        + cameraPojo.getEndtime() + "]");
                return true;
            } else {
                errorcode = NetSDKLib.NETSDK_INSTANCE.CLIENT_GetLastError();
                logger.error("dhsdk 按时间回放录像文件失败,错误码:" + ToolKits.getErrorCode() + " 设备信息：[ip:" + cameraPojo.getIp() + " port:"
                        + cameraPojo.getPort() + " channel:" + cameraPojo.getChannel() + " statrtime:" + cameraPojo.getStarttime() + " endtime:"
                        + cameraPojo.getEndtime() + "]");
                return false;
            }
        }

    }


    /**
     * @description 格式化时间
     * @author LianYanFei
     * @date 2023/12/20
     */
    public static NetSDKLib.NET_TIME handleDate(String odate, String otime) {
        NetSDKLib.NET_TIME net_time = new NetSDKLib.NET_TIME();
        String[] odates = odate.split("-");
        int year = Integer.parseInt(odates[0]);
        int month = Integer.parseInt(odates[1]);
        int day = Integer.parseInt(odates[2]);

        String[] otbegins = otime.split(":");
        net_time.dwYear = year;
        net_time.dwMonth = month;
        net_time.dwDay = day;
        net_time.dwHour = Integer.parseInt(otbegins[0]);
        net_time.dwMinute = Integer.parseInt(otbegins[1]);
        net_time.dwSecond = Integer.parseInt(otbegins[2]);

        return net_time;
    }


}
