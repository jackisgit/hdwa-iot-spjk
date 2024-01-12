package com.wanda.epc.cache;

import com.netsdk.lib.NetSDKLib;
import com.wanda.epc.callback.PlayDataCallBack;
import com.wanda.epc.callback.RealDataCallBack;
import com.wanda.epc.play.HlsPush;
import com.wanda.epc.play.PlayBackCapture;
import com.wanda.epc.play.RtmpPush;
import com.wanda.epc.pojo.CameraPojo;
import com.wanda.epc.sdk.DHLoginSDK;
import com.sun.jna.NativeLong;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author LianYanFei
 * @Title CacheUtil.java
 * @description 推流缓存信息
 * @time 2023年11月28日 下午3:17:16
 **/
public class CacheUtil {
    /*
     * 保存已经开始推送的流
     */
    public static Map<String, CameraPojo> STREATMAP = new ConcurrentHashMap<>();

    /*
     * 保存设备预览注册句柄
     */
    public static Map<String, DHLoginSDK> LOGINSDK = new ConcurrentHashMap<>();




    /*
     * 保存RTMP push
     */
    public static Map<String, RtmpPush> PUSHRTMPMAP = new ConcurrentHashMap<>();
    /*
     * 保存HLS push
     */
    public static Map<String, HlsPush> PUSHHLSMAP = new ConcurrentHashMap<>();
    /*
     * 保存服务启动时间
     */
    public static long STARTTIME;

    /*
     * 保存录像文件下载句柄
     */
    public static Map<String, NativeLong> DWONLOADHANDLE = new ConcurrentHashMap<>();

    /*
     * 保存录像文件下载地址
     */
    public static Map<String, String> DWONLOADPATH = new ConcurrentHashMap<>();

    /*
     * 保存历史回放的callback
     */
    public static Map<String, PlayDataCallBack> HISTORYCALLBACK = new ConcurrentHashMap<>();

    /*
     * 保存直播的callback
     */
    public static Map<String, RealDataCallBack> LIVECALLBACK = new ConcurrentHashMap<>();

    /*
     * 保存抓图的类
     */
    public static Map<String, PlayBackCapture> PLAYBACKCAPTURE = new ConcurrentHashMap<>();

    /*
     * 保存拖动前的解码时间戳
     */
    public static Map<String, Long> DTSMAP = new ConcurrentHashMap<>();


    //=============LianYanFei 2023-12-23 新增预览时登录句柄，回放时登录句柄，查询录像文件登录句柄============//

    /*
     * 预览登录句柄
     */
    public static Map<String, NetSDKLib.LLong> REAL_PLAY_LOGIN_MODULE = new ConcurrentHashMap<>();

    /*
     * 回放登录句柄
     */
    public static Map<String, DHLoginSDK> PLAY_BACK_LOGIN_MODULE = new ConcurrentHashMap<>();


    /*
     * 文件查询登录句柄
     */
    public static Map<String, NetSDKLib.LLong> FIND_FILE_LOGIN_MODULE = new ConcurrentHashMap<>();
}
