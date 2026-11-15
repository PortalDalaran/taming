package io.github.portaldalaran.taming.support;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.github.portaldalaran.talons.core.TalonsHelper;
import io.github.portaldalaran.talons.meta.AssociationTableInfo;
import io.github.portaldalaran.taming.pojo.PageCriteria;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;

/**
 * 测试用实体与查询 VO，并负责初始化 MyBatis-Plus 的 TableInfo，
 * 使 {@code BuildUtils#getColumn} 能在纯单元测试中解析出真实列名。
 *
 * <p>{@code ColumnsBuilder} 会调用 {@code TalonsHelper.init}，后者在真实 Spring 环境中
 * 通过 {@code SqlSessionFactory} 读取 Mapper 注册信息；纯单元测试没有该 Bean，
 * 因此这里预置一个"无关联字段"的 {@link AssociationTableInfo}，等价于生产环境中
 * 实体未声明任何 {@code @ManyToOne} 等关联注解时的结果。
 */
public final class TestEntities {

    private TestEntities() {
    }

    @Data
    @TableName("t_user")
    public static class UserDO {
        private Long id;
        private String name;
        private Integer age;
        @TableField("sex_code")
        private String sex;
        @TableField("created_at")
        private LocalDateTime createdAt;
        private LocalDate birthday;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class UserCriteria extends PageCriteria<UserDO> {
        private String name;
        private Integer age;
        private String sex;
        private LocalDateTime createdAt;
        private LocalDate birthday;
    }

    /** 关联查询用的第二个实体，用于 joinClassList 白名单测试。 */
    @Data
    @TableName("t_dept")
    public static class DeptDO {
        private Long id;
        @TableField("dept_name")
        private String deptName;
    }

    /**
     * TableInfoHelper / TalonsHelper 都是全局静态缓存，重复初始化会互相干扰，
     * 因此只初始化一次并复用。
     */
    public static synchronized void initTableInfo() {
        if (TableInfoHelper.getTableInfo(UserDO.class) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), ""), UserDO.class);
        }
        if (TableInfoHelper.getTableInfo(DeptDO.class) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), ""), DeptDO.class);
        }
        initTalonsAssociationInfo();
    }

    @SuppressWarnings("unchecked")
    private static void initTalonsAssociationInfo() {
        try {
            Field cache = TalonsHelper.class.getDeclaredField("ASSOCIATION_TABLE_MAP");
            cache.setAccessible(true);
            Map<Class<?>, AssociationTableInfo> map = (Map<Class<?>, AssociationTableInfo>) cache.get(null);
            map.computeIfAbsent(UserDO.class, clazz -> {
                AssociationTableInfo<UserDO> info = new AssociationTableInfo<>();
                info.setName(clazz.getSimpleName());
                info.setTableClass(UserDO.class);
                info.setOneToManys(new ArrayList<>());
                info.setManyToOnes(new ArrayList<>());
                info.setManyToManys(new ArrayList<>());
                return info;
            });
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to prepare TalonsHelper association cache", e);
        }
    }
}
