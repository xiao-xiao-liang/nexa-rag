package com.nexarag.auth.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexarag.auth.model.dataobject.SecurityAuditEventDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDateTime;
import java.util.List;
/** 安全审计事件数据访问 Mapper。 */
@Mapper public interface SecurityAuditEventMapper extends BaseMapper<SecurityAuditEventDO> {
    @Delete("DELETE FROM auth_security_audit_event WHERE create_time < #{before}") int deleteBefore(@Param("before") LocalDateTime before);
    @Select("SELECT event_id,user_id,actor_user_id,event_type,event_result,device_session_id,masked_ip,city,detail_summary,create_time FROM auth_security_audit_event WHERE user_id=#{userId} AND create_time>=#{since} ORDER BY create_time DESC") List<SecurityAuditEventDO> selectByUserIdSince(@Param("userId") Long userId,@Param("since") LocalDateTime since);
}
