package io.github.portaldalaran.taming.core;

/**
 * 请求参数解密策略接口，由框架使用方实现并注入。
 *
 * @author aohee@163.com
 */
@FunctionalInterface
public interface BodyDecryptor {

    /**
     * 解密字符串，返回明文
     *
     * @param encrypted 密文
     * @return 明文
     */
    String decrypt(String encrypted);
}
