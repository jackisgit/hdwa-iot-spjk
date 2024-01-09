package com.wanda.epc.request;

import lombok.Data;

/**
 * @author LianYanFei
 * @version 1.0
 * @project iot-epc-new
 * @description 回放请求参数
 * @date 2023/6/12 16:58:52
 */
@Data
public class DahuaPlaybackRequest {

    private String eqId;

    private String beginTime;

    private String endTime;
}
