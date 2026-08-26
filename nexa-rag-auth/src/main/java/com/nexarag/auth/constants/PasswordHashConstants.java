package com.nexarag.auth.constants;

/**
 * 本地密码 Argon2id 哈希参数。
 */
public final class PasswordHashConstants {

    /** 随机盐长度，单位为字节。 */
    public static final int SALT_LENGTH = 16;

    /** 哈希长度，单位为字节。 */
    public static final int HASH_LENGTH = 32;

    /** 并行度。 */
    public static final int PARALLELISM = 1;

    /** 内存成本，单位为 KiB。 */
    public static final int MEMORY_KIB = 19_456;

    /** 迭代次数。 */
    public static final int ITERATIONS = 2;

    private PasswordHashConstants() {
    }
}
