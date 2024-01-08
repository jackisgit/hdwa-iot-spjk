package com.wanda.epc.pojo;

import lombok.Data;

import java.util.List;

/**
 * @author LianYanFei
 * @version 1.0
 * @project iot-epc-new
 * @description 海康威视回放data
 * @date 2023/6/9 10:19:04
 */
@Data
public class HikvisionPlayBackDataPojo {

    private String uuid;

    private String url;

    private List<HikvisionPlayBackLisPojo> list;
}
