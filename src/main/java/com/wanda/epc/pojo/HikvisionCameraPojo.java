package com.wanda.epc.pojo;

import lombok.Data;

/**
 *@description 海康威视视频监控
 *@author LianYanFei
 *@date 2023/6/29
 */
@Data
public class HikvisionCameraPojo {


    private String code;

    private String msg;

    private HikvisionPlayBackDataPojo data;
}
