package com.wanda.epc.cache;

import com.wanda.epc.callback.RealDataCallBack;
import com.wanda.epc.play.HlsPush;
import com.wanda.epc.play.RtmpPush;
import com.wanda.epc.pojo.CameraPojo;
import com.wanda.epc.sdk.DHLoginSDK;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author LianYanFei
 * @version 1.0
 * @project iot_epc_spdj
 * @description 推流缓存信息
 * @date 2023/10/18 16:29:08
 */
public class CacheUtil {

    /*
     * 保存HLS push
     */
    public static Map<String, HlsPush> PUSHHLSMAP = new ConcurrentHashMap<>();


    /*
     * 保存已经开始推送的流
     */
    public static Map<String, CameraPojo> STREATMAP = new ConcurrentHashMap<>();

    /*
     * 保存设备注册句柄
     */
    public static Map<String, DHLoginSDK> LOGINSDK = new ConcurrentHashMap<>();

    /*
     * 保存RTMP push
     */
    public static Map<String, RtmpPush> PUSHRTMPMAP = new ConcurrentHashMap<>();


    /*
     * 保存服务启动时间
     */
    public static long STARTTIME;


    /*
     * 保存直播的callback
     */
    public static Map<String, RealDataCallBack> LIVECALLBACK = new ConcurrentHashMap<>();

    /*
     * 保存拖动前的解码时间戳
     */
    public static Map<String, Long> DTSMAP = new ConcurrentHashMap<>();
}
