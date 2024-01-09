package com.wanda.epc.pojo;

import lombok.Data;

/**
 * @author LianYanFei
 * @version 1.0
 * @project iot-epc-new
 * @description 大华股份回放list
 * @date 2023/6/9 10:17:52
 */
@Data
public class DahuaPlayBackListDto {

    private String beginTime;

    private String endTime;

    private int size;
}
