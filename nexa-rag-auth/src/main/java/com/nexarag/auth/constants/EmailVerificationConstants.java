package com.nexarag.auth.constants;

/**
 * 邮箱验证码的安全策略与 Redis 键前缀常量。
 */
public final class EmailVerificationConstants {

    /** 验证码长度。 */
    public static final int CODE_LENGTH = 6;

    /** 验证码有效分钟数。 */
    public static final long CODE_TTL_MINUTES = 5;

    /** 同邮箱、同用途最小重发间隔秒数。 */
    public static final long RESEND_INTERVAL_SECONDS = 60;

    /** 单个挑战最多允许验证次数。 */
    public static final int MAX_VERIFY_ATTEMPTS = 5;

    /** 单个邮箱在自然日内跨用途的最大发送次数。 */
    public static final int MAX_DAILY_SEND_COUNT = 10;

    /** Redis 中验证码哈希键前缀。 */
    public static final String CHALLENGE_KEY_PREFIX = "auth:email:challenge:";

    /** Redis 中重发限制键前缀。 */
    public static final String RESEND_KEY_PREFIX = "auth:email:resend:";

    /** Redis 中自然日发送计数键前缀。 */
    public static final String DAILY_COUNT_KEY_PREFIX = "auth:email:daily:";

    private EmailVerificationConstants() {
    }
}
