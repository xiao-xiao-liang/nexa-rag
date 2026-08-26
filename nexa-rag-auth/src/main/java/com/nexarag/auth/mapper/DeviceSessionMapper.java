package com.nexarag.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexarag.auth.model.dataobject.DeviceSessionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/** 设备会话摘要数据访问 Mapper。 */
@Mapper
public interface DeviceSessionMapper extends BaseMapper<DeviceSessionDO> {
    @Select("SELECT device_session_id,user_id,sa_token_session_key_hash,device_id_hash,device_name,device_label,masked_ip,city,login_time,last_active_time,revoked_time FROM auth_device_session WHERE user_id=#{userId} AND revoked_time IS NULL ORDER BY last_active_time DESC")
    List<DeviceSessionDO> selectActiveByUserId(@Param("userId") Long userId);

    @Select("SELECT device_session_id,user_id,sa_token_session_key_hash,device_id_hash,device_name,device_label,masked_ip,city,login_time,last_active_time,revoked_time FROM auth_device_session WHERE device_session_id=#{deviceSessionId} AND user_id=#{userId} AND revoked_time IS NULL FOR UPDATE")
    DeviceSessionDO selectActiveByIdAndUserIdForUpdate(@Param("deviceSessionId") Long deviceSessionId, @Param("userId") Long userId);

    @Update("UPDATE auth_device_session SET revoked_time=#{now} WHERE user_id=#{userId} AND revoked_time IS NULL")
    int revokeActiveByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Update("UPDATE auth_device_session SET revoked_time=#{now} WHERE sa_token_session_key_hash=#{tokenHash} AND revoked_time IS NULL")
    int revokeActiveByTokenHash(@Param("tokenHash") String tokenHash, @Param("now") LocalDateTime now);

    @Update("UPDATE auth_device_session SET last_active_time=#{now} WHERE sa_token_session_key_hash=#{tokenHash} AND revoked_time IS NULL")
    int touchActiveByTokenHash(@Param("tokenHash") String tokenHash, @Param("now") LocalDateTime now);
}
