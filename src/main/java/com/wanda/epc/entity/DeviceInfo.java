package com.wanda.epc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("device_info")
public class DeviceInfo {


    /**
     * 数据库自增ID
     */

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * IP地址
     */
    @TableField(value = "ip")
    private String ip;

    /**
     * 设备端口
     */
    @TableField(value = "port")
    private String port;


    /**
     * 设备账号
     */
    @TableField(value = "account")
    private String account;

    /**
     * 设备密码
     */
    @TableField(value = "password")
    private String password;

    /**
     * 通道id
     */
    @TableField(value = "channel")
    private String channel;

    /**
     * 厂商类型 dahua  hikvision
     */
    @TableField(value = "tradType")
    private String tradType;

    /**
     * 与慧云系统设备id关联
     */
    @TableField(value = "eqId")
    private String eqId;

    /**
     * 通道名
     */
    @TableField(value = "cameraName")
    private String cameraName;








}
