package com.netsdk.lib.structure;


import com.netsdk.lib.NetSDKLib;

/**
 * @author 260611
 * @description 事件类型 EVENT_IVS_TEMPERATURE_ALARM(温度报警事件)对应的数据块描述信息
 * @date 2023/01/12 10:19:13
 */
public class NET_DEV_EVENT_TEMPERATURE_ALARM_INFO extends NetSDKLib.SdkStructure {
    /**
     * 通道号
     */
    public int nChannelID;
    /**
     * 事件类型 0:脉冲,1:开始, 2:停止
     */
    public int nAction;
    /**
     * 事件发生的时间
     */
    public NET_TIME_EX stuUTC = new NET_TIME_EX();
    /**
     * 名称
     */
    public byte[] szName = new byte[128];
    /**
     * 当前温度
     */
    public float fCurrent;
    /**
     * 正常范围
     */
    public int[] nRange = new int[2];
    /**
     * GPS信息
     */
    public NetSDKLib.NET_GPS_STATUS_INFO stuGPS = new NetSDKLib.NET_GPS_STATUS_INFO();
    /**
     * 预留字节
     */
    public byte[] szReserved = new byte[1024];

    public NET_DEV_EVENT_TEMPERATURE_ALARM_INFO() {
    }
}