package com.wanda.epc.pojo;

import lombok.Data;

/**
 *@description 大华股份视频监控
 *@author LianYanFei
 *@date 2023/6/29
 */
@Data
public class DahuaCameraDto {


    private String code;

    private String msg;

    private DahuaPlayBackDataDto data;
}
