package com.wanda.epc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wanda.epc.entity.DeviceInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author LianYanFei
 * @version 1.0
 * @project iot-epc-module
 * @description 设备mapper
 * @date 2023/6/29 16:50:17
 */
@Mapper
public interface DeviceInfoMapper extends BaseMapper<DeviceInfo> {
}
