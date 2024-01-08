package com.wanda.epc.pojo;

import lombok.Data;

/**
 * @author LianYanFei
 * @version 1.0
 * @project iot-epc-module
 * @description 海康威视设备信息
 * @date 2023/4/12 13:47:19
 */
@Data
public class HikvisionDevicePojo {

    /**
     * 与慧云系统设备id关联
     */
    private String eqId;

    /**
     * 通道名
     */
    private String name;

    private String url;

    private String deviceID;
    private String channelId;
    private String flv;
    private String ws_flv;
    private String hls;


}
