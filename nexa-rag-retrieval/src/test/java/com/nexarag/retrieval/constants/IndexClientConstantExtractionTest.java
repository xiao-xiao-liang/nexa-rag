package com.nexarag.retrieval.constants;

import com.nexarag.retrieval.index.keyword.ElasticsearchKeywordIndexClient;
import com.nexarag.retrieval.index.vector.MilvusVectorIndexClient;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 索引客户端常量抽取测试，防止中间件字段名和长度限制继续散落在客户端实现类中。
 */
class IndexClientConstantExtractionTest {

    @Test
    void indexClientsShouldNotDeclarePrivateStaticFinalConstants() {
        assertThat(privateStaticFinalFieldNames(ElasticsearchKeywordIndexClient.class)).isEmpty();
        assertThat(privateStaticFinalFieldNames(MilvusVectorIndexClient.class)).isEmpty();
    }

    private String[] privateStaticFinalFieldNames(Class<?> targetClass) {
        return Arrays.stream(targetClass.getDeclaredFields())
                .filter(field -> Modifier.isPrivate(field.getModifiers()))
                .filter(field -> Modifier.isStatic(field.getModifiers()))
                .filter(field -> Modifier.isFinal(field.getModifiers()))
                .filter(field -> !"log".equals(field.getName()))
                .map(field -> targetClass.getSimpleName() + "." + field.getName())
                .toArray(String[]::new);
    }
}
