package com.wanda.epc.request;


import lombok.Data;

/**
 * @author LianYanFei
 * @version 1.0
 * @project iot-epc-new
 * @description 大华股份云台控制请求参数
 * @date 2023/6/15 10:51:03
 */
@Data
public class DahuaControllingRequest {

    private String eqId;
    private String command;
    private Integer action;
}
