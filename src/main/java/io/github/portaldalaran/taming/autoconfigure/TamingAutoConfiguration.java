package io.github.portaldalaran.taming.autoconfigure;

import io.github.portaldalaran.taming.core.BodyDecryptor;
import io.github.portaldalaran.taming.core.QueryCriteriaMethodArgumentResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.OrderComparator;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * 自动注册 {@link QueryCriteriaMethodArgumentResolver}。
 * <p>
 * 若存在 {@link BodyDecryptor} Bean，则注入加密解析；可用 {@code taming.web.enabled=false} 关闭。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(WebMvcConfigurer.class)
@ConditionalOnProperty(prefix = "taming.web", name = "enabled", matchIfMissing = true)
public class TamingAutoConfiguration implements WebMvcConfigurer {

    private final BodyDecryptor decryptor;

    public TamingAutoConfiguration(ObjectProvider<BodyDecryptor> decryptorProvider) {
        this.decryptor = decryptorProvider.getIfAvailable();
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        if (decryptor != null) {
            resolvers.add(new QueryCriteriaMethodArgumentResolver(decryptor));
        } else {
            resolvers.add(new QueryCriteriaMethodArgumentResolver());
        }
        OrderComparator.sort(resolvers);
    }
}
