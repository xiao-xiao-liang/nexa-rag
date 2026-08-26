package com.nexarag.auth.service;

/**
 * 本地密码哈希和验证服务。
 */
public interface PasswordService {

    /**
     * 校验密码规则并生成 Argon2id 哈希。
     *
     * @param rawPassword 明文密码
     * @return PHC 格式密码哈希
     */
    String hash(String rawPassword);

    /**
     * 为已经验证成功的历史密码重新生成当前参数的哈希。
     *
     * @param rawPassword 已验证成功的明文密码
     * @return 当前参数的 PHC 格式密码哈希
     */
    String rehashVerified(String rawPassword);

    /**
     * 比对明文密码和已保存的哈希。
     *
     * @param rawPassword 明文密码
     * @param passwordHash 已保存的 PHC 密码哈希
     * @return 密码是否匹配
     */
    boolean matches(String rawPassword, String passwordHash);

    /**
     * 判断已保存哈希是否需要按当前参数重新生成。
     *
     * @param passwordHash 已保存的 PHC 密码哈希
     * @return 是否需要重新哈希
     */
    boolean shouldUpgrade(String passwordHash);
}
