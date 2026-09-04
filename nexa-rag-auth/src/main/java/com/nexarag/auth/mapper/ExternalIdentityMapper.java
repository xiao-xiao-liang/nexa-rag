package com.nexarag.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexarag.auth.model.dataobject.ExternalIdentityDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 第三方身份绑定数据访问 Mapper。 */
@Mapper
public interface ExternalIdentityMapper extends BaseMapper<ExternalIdentityDO> {
    /** 锁定提供方稳定主体的绑定关系。 */
    @Select("""
            SELECT external_identity_id, user_id, provider_code, provider_subject, bind_time, create_time, update_time
            FROM auth_external_identity WHERE provider_code = #{providerCode} AND provider_subject = #{providerSubject}
            FOR UPDATE
            """)
    ExternalIdentityDO selectByProviderAndSubjectForUpdate(@Param("providerCode") String providerCode,
                                                            @Param("providerSubject") String providerSubject);

    /** 锁定用户在指定提供方上的绑定，保证同一用户单个平台最多绑定一个账号。 */
    @Select("""
            SELECT external_identity_id, user_id, provider_code, provider_subject, bind_time, create_time, update_time
            FROM auth_external_identity WHERE user_id = #{userId} AND provider_code = #{providerCode}
            FOR UPDATE
            """)
    ExternalIdentityDO selectByUserIdAndProviderCodeForUpdate(@Param("userId") Long userId,
                                                                @Param("providerCode") String providerCode);

    /**
     * 按身份 ID 游标分页读取第三方绑定，用于低峰重建 BloomFilter。
     *
     * @param lastExternalIdentityId 上一页最大身份 ID；首次传 0
     * @param limit                  单页条数
     * @return 当前页第三方身份绑定
     */
    @Select("""
            SELECT external_identity_id, user_id, provider_code, provider_subject, bind_time, create_time, update_time
            FROM auth_external_identity
            WHERE external_identity_id > #{lastExternalIdentityId}
            ORDER BY external_identity_id
            LIMIT #{limit}
            """)
    List<ExternalIdentityDO> selectAfterExternalIdentityId(
            @Param("lastExternalIdentityId") long lastExternalIdentityId, @Param("limit") long limit);

    /** 查询用户当前的所有第三方身份绑定。 */
    @Select("""
            SELECT external_identity_id, user_id, provider_code, provider_subject, bind_time, create_time, update_time
            FROM auth_external_identity WHERE user_id = #{userId}
            """)
    List<ExternalIdentityDO> selectByUserId(@Param("userId") Long userId);

    /** 锁定用户全部第三方身份，用于与邮箱、密码凭据一起裁决最后登录方式。 */
    @Select("""
            SELECT external_identity_id, user_id, provider_code, provider_subject, bind_time, create_time, update_time
            FROM auth_external_identity WHERE user_id = #{userId} FOR UPDATE
            """)
    List<ExternalIdentityDO> selectByUserIdForUpdate(@Param("userId") Long userId);

    /** 删除指定用户的一条指定提供方身份。 */
    @Delete("""
            DELETE FROM auth_external_identity
            WHERE external_identity_id = #{externalIdentityId}
              AND user_id = #{userId}
              AND provider_code = #{providerCode}
            """)
    int deleteByIdAndUserIdAndProviderCode(@Param("externalIdentityId") Long externalIdentityId,
                                           @Param("userId") Long userId,
                                           @Param("providerCode") String providerCode);
}
