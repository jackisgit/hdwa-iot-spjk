package com.wanda.epc.play;

import com.alibaba.fastjson.JSON;
import com.wanda.epc.cache.CacheUtil;
import com.wanda.epc.callback.PlayDataCallBack;
import com.wanda.epc.callback.RealDataCallBack;
import com.wanda.epc.config.Config;
import com.wanda.epc.pojo.CameraPojo;
import com.wanda.epc.sdk.HCLoginSDK;
import com.wanda.epc.sdk.HCNetSDK;
import com.wanda.epc.util.Utils;
import com.sun.jna.NativeLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

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

    private HCLoginSDK login;
    private CameraPojo cameraPojo;// 设备信息
    private int errorcode = 0;// 错误码
    private RealDataCallBack realDataCallBack;// 预览回调函数
    private PlayDataCallBack fPlayDataCallBack;// 历史回放回调函数
    private NativeLong lRealPlayHandle;// 实时预览播放句柄
    private NativeLong lHisPlayHandle;// 历史回放播放句柄
    private NativeLong lFindFileHandle;// 文件查找句柄
    private NativeLong lFindFileNextHandle;// 逐个查找文件信息返回值
    private HCNetSDK.NET_DVR_TIME lpStartTime;// 时间参数结构体(开始时间)
    private HCNetSDK.NET_DVR_TIME lpStopTime;// 时间参数结构体(结束时间)
    private int playSign;// 直播和历史回放标志;0:直播,1:历史回放
    private PipedInputStream inputStream;// 管道输入流
    private PipedOutputStream outputStream;// 管道输出流
    private HCNetSDK.NET_DVR_FINDDATA_V30 findData;// 录像文件信息结构体。

    public RealPlay(CameraPojo cameraPojo, HCLoginSDK login) {
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
     * @Description:海康sdk接口调用
     **/
    public boolean playsdk() {
        if (null == cameraPojo.getStarttime()) {
            playSign = 0;// 直播标志
            // 直播
            realDataCallBack = new RealDataCallBack(outputStream);
            // 实时预览
            lRealPlayHandle = HCNetSDK.INSTANCE.NET_DVR_RealPlay_V40(login.getLUserID(), cameraPojo.getPreviewinfo(),
                    realDataCallBack, null);
            errorcode = HCNetSDK.INSTANCE.NET_DVR_GetLastError();
            if (lRealPlayHandle.longValue() < 0 && errorcode != 0) {
                logger.error("hcsdk 实时预览失败,错误码：" + errorcode + " 设备信息：[ip:" + cameraPojo.getIp() + " port:"
                        + cameraPojo.getPort() + " channel:" + cameraPojo.getChannel() + " stream:"
                        + cameraPojo.getStream() + "]");
                return false;
            } else {
                // 将callBack保存在缓存中
                CacheUtil.LIVECALLBACK.put(cameraPojo.getToken(), realDataCallBack);

                logger.info("hcsdk 实时预览成功  设备信息：[ip:" + cameraPojo.getIp() + " port:" + cameraPojo.getPort()
                        + " channel:" + cameraPojo.getChannel() + " stream:" + cameraPojo.getStream() + "]");
                return true;
            }
        } else {
            playSign = 1;// 历史回放标志
            // 历史回放
            lpStartTime = new HCNetSDK.NET_DVR_TIME();
            lpStopTime = new HCNetSDK.NET_DVR_TIME();

            lpStartTime = Utils.getNvrTime(cameraPojo.getStarttime());
            lpStopTime = Utils.getNvrTime(cameraPojo.getEndtime());
            // 根据时间检测录像文件
            lFindFileHandle = HCNetSDK.INSTANCE.NET_DVR_FindFile(login.getLUserID(),
                    new NativeLong(new Integer(cameraPojo.getChannel())), 0, lpStartTime, lpStopTime);
            logger.info("lFindFileHandle：{}", lFindFileHandle.intValue());
            if (lFindFileHandle.intValue() < 0) {
                errorcode = HCNetSDK.INSTANCE.NET_DVR_GetLastError();
                logger.error("hcsdk 按时间查找录像文件失败,错误码:" + errorcode + " 设备信息：[ip:" + cameraPojo.getIp() + " port:"
                        + cameraPojo.getPort() + " channel:" + cameraPojo.getChannel() + " stream:"
                        + cameraPojo.getStream() + " statrtime:" + cameraPojo.getStarttime() + " endtime:"
                        + cameraPojo.getEndtime() + "]");
                return false;
            }
            findData = new HCNetSDK.NET_DVR_FINDDATA_V30();
            // 异步操作，需要等待sdk给findData赋值
            while (true) {
                lFindFileNextHandle = HCNetSDK.INSTANCE.NET_DVR_FindNextFile_V30(lFindFileHandle, findData);
                if (lFindFileNextHandle.intValue() != 1002) {
                    break;
                }
            }
            if (lFindFileNextHandle.intValue() != 1000) {
                errorcode = HCNetSDK.INSTANCE.NET_DVR_GetLastError();
                logger.error("hcsdk 按时间逐个获取查找到的文件信息失败,错误码:" + errorcode + " 设备信息：[ip:" + cameraPojo.getIp() + " port:"
                        + cameraPojo.getPort() + " channel:" + cameraPojo.getChannel() + " stream:"
                        + cameraPojo.getStream() + " statrtime:" + cameraPojo.getStarttime() + " endtime:"
                        + cameraPojo.getEndtime() + "]");
                return false;
            }
            // 关闭文件查找，释放资源。
            HCNetSDK.INSTANCE.NET_DVR_FindClose_V30(lFindFileHandle);
            // 按时间回放录像
            lHisPlayHandle = HCNetSDK.INSTANCE.NET_DVR_PlayBackByTime(login.getLUserID(),
                    new NativeLong(new Integer(cameraPojo.getChannel())), lpStartTime, lpStopTime, null);
            if (lHisPlayHandle.longValue() < 0) {
                errorcode = HCNetSDK.INSTANCE.NET_DVR_GetLastError();
                logger.error("hcsdk 按时间回放录像文件失败,错误码:" + errorcode + " 设备信息：[ip:" + cameraPojo.getIp() + " port:"
                        + cameraPojo.getPort() + " channel:" + cameraPojo.getChannel() + " stream:"
                        + cameraPojo.getStream() + " statrtime:" + cameraPojo.getStarttime() + " endtime:"
                        + cameraPojo.getEndtime() + "]");
                return false;
            } else {
                // 保存回放句柄
                cameraPojo.setlHisPlayHandle(lHisPlayHandle);
                fPlayDataCallBack = new PlayDataCallBack(outputStream);

                CacheUtil.HISTORYCALLBACKHANDLE.put(cameraPojo.getIp().concat("_HK_PLAYBACK_HANDLE"),lHisPlayHandle);

                // 将callBack保存在缓存中
                CacheUtil.HISTORYCALLBACK.put(cameraPojo.getToken(), fPlayDataCallBack);

                // 注册回调函数
                boolean isCallBack = HCNetSDK.INSTANCE.NET_DVR_SetPlayDataCallBack(lHisPlayHandle, fPlayDataCallBack,
                        0);
                if (!isCallBack) {
                    errorcode = HCNetSDK.INSTANCE.NET_DVR_GetLastError();
                    logger.error("hcsdk 注册回调函数失败,错误码:" + errorcode + " 设备信息：[ip:" + cameraPojo.getIp() + " port:"
                            + cameraPojo.getPort() + " channel:" + cameraPojo.getChannel() + " stream:"
                            + cameraPojo.getStream() + " statrtime:" + cameraPojo.getStarttime() + " endtime:"
                            + cameraPojo.getEndtime() + "]");
                    return false;
                }
                // 控制录像回放状态 开始回放
                boolean isControl = HCNetSDK.INSTANCE.NET_DVR_PlayBackControl(lHisPlayHandle,
                        HCNetSDK.NET_DVR_PLAYSTART, 0, null);
                if (isControl) {

                    logger.info("hcsdk 按时间回放录像文件成功" + " 设备信息：[ip:" + cameraPojo.getIp() + " port:"
                            + cameraPojo.getPort() + " channel:" + cameraPojo.getChannel() + " stream:"
                            + cameraPojo.getStream() + " statrtime:" + cameraPojo.getStarttime() + " endtime:"
                            + cameraPojo.getEndtime() + "]");
                    // 控制历史回放拉流推流时的速度和直播一致
//					HCNetSDK.INSTANCE.NET_DVR_PlayBackControl(lHisPlayHandle, HCNetSDK.NET_DVR_SETSPEED,
//							config.getBitrate(), null);
                    return true;
                } else {
                    errorcode = HCNetSDK.INSTANCE.NET_DVR_GetLastError();
                    logger.error("hcsdk 控制录像回放状态失败,错误码:" + errorcode + " 设备信息：[ip:" + cameraPojo.getIp() + " port:"
                            + cameraPojo.getPort() + " channel:" + cameraPojo.getChannel() + " stream:"
                            + cameraPojo.getStream() + " statrtime:" + cameraPojo.getStarttime() + " endtime:"
                            + cameraPojo.getEndtime() + "]");
                    return false;
                }
            }
        }
    }



}
