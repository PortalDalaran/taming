package io.github.portaldalaran.taming.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.portaldalaran.taming.core.QueryCriteriaException;
import io.github.portaldalaran.taming.pojo.QueryCriteriaParam;
import io.github.portaldalaran.taming.support.TestEntities;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工具类与一对一排序器的覆盖测试。
 */
class UtilsCoverageTest {

    @Test
    void isCommonField_isCaseInsensitiveAndNullSafe() {
        assertTrue(BuildUtils.isCommonField("pageNo"));
        assertTrue(BuildUtils.isCommonField("PAGESIZE"));
        assertTrue(BuildUtils.isCommonField("ORDERBY"));
        assertTrue(BuildUtils.isCommonField("sortOrder"));
        assertTrue(BuildUtils.isCommonField("fields"));
        assertFalse(BuildUtils.isCommonField("name"));
        assertFalse(BuildUtils.isCommonField(null));
    }

    @Test
    void getAllDeclaredFields_includesSuperclassesAndDeduplicates() {
        List<Field> fields = BuildUtils.getAllDeclaredFields(TestEntities.UserCriteria.class);
        List<String> names = fields.stream().map(Field::getName).toList();
        assertTrue(names.contains("name"));
        assertTrue(names.contains("fields"));
        assertTrue(names.contains("orderBy"));
        assertTrue(names.contains("pageNo"));
        assertEquals(names.size(), names.stream().distinct().count());
        assertSame(fields, BuildUtils.getAllDeclaredFields(TestEntities.UserCriteria.class));
        assertThrows(IllegalArgumentException.class, () -> BuildUtils.getAllDeclaredFields(null));
    }

    @Test
    void loadValueArrays_handlesCollectionsStringsAndNull() {
        assertArrayEquals(new Object[]{1, 2},
                BuildUtils.loadValueArrays(Arrays.asList(1, 2)));
        assertArrayEquals(new Object[]{"a", "b", "c"},
                BuildUtils.loadValueArrays("a,b,c"));
        assertEquals(0, BuildUtils.loadValueArrays("").length);
        assertThrows(QueryCriteriaException.class, () -> BuildUtils.loadValueArrays(null));
    }

    @Test
    void getFieldName_resolvesLambdaGetter() {
        assertEquals("name", BuildUtils.getFieldName(TestEntities.UserDO::getName));
        assertEquals("createdAt", BuildUtils.getFieldName(TestEntities.UserDO::getCreatedAt));
    }

    @Test
    void getColumn_handlesNullAndUnknownFields() {
        TestEntities.initTableInfo();
        assertEquals("name", BuildUtils.getColumn("name", TestEntities.UserDO.class));
        assertEquals("sex_code", BuildUtils.getColumn("sex", TestEntities.UserDO.class));
        assertNull(BuildUtils.getColumn(null, TestEntities.UserDO.class));
        assertNull(BuildUtils.getColumn("name", null));
        assertNull(BuildUtils.getColumn("unknownField", TestEntities.UserDO.class));
    }

    @Test
    void checkSqlInjection_scansNestedCollectionsParamsAndArrays() {
        QueryCriteriaParam<Object> nested =
                new QueryCriteriaParam<>("0", null, "1=1; select * from user");
        assertThrows(QueryCriteriaException.class, () -> BuildUtils.checkSqlInjection(
                "applySql", QueryConstants.APPLY_SQL, List.of(nested)));
        assertThrows(QueryCriteriaException.class, () -> BuildUtils.checkSqlInjection(
                "applySql", QueryConstants.APPLY_SQL,
                new Object[]{"ok", "1=1; select * from user"}));
        assertThrows(QueryCriteriaException.class, () -> BuildUtils.checkSqlInjection(
                "applySql", QueryConstants.APPLY_SQL,
                List.of("ok", "1=1; select * from user")));
        assertDoesNotThrow(() -> BuildUtils.checkSqlInjection(
                "applySql", QueryConstants.APPLY_SQL,
                "date_format(dateColumn,'%Y-%m-%d') = {0}", "2008-08-08"));
        // 普通算子走参数绑定，不扫描 value
        assertDoesNotThrow(() -> BuildUtils.checkSqlInjection(
                "name", QueryConstants.LIKE, "O'Brien"));
    }

    @Test
    void deprecatedCheckSqlInjection_delegatesToNewOverload() {
        assertThrows(QueryCriteriaException.class,
                () -> BuildUtils.checkSqlInjection("name';--", "x"));
    }

    @Test
    void classUtils_infersGenericAndListsFields() {
        assertEquals(String.class, ClassUtils.getEntity(new ArrayList<String>() {
        }.getClass()));
        assertNull(ClassUtils.getEntity(Object.class));
        assertNotNull(ClassUtils.getAllDeclaredFields(TestEntities.UserCriteria.class));
        assertThrows(IllegalArgumentException.class, () -> ClassUtils.getAllDeclaredFields(null));
    }

    @Test
    void charSequenceUtils_convertsFirstLetter() {
        assertEquals("name", CharSequenceUtils.lowerFirst("Name"));
        assertEquals("name", CharSequenceUtils.lowerFirst("name"));
        assertEquals("Name", CharSequenceUtils.upperFirst("name"));
        assertEquals("Name", CharSequenceUtils.upperFirst("Name"));
    }

    @Test
    void jsonUtils_parsesSerializesAndToleratesEmptyInput() {
        assertNull(JsonUtils.parseObject("", Map.class));
        assertNull(JsonUtils.parseObject(null, Map.class));
        assertNull(JsonUtils.toJsonString(null));
        assertEquals("plain", JsonUtils.toJsonString("plain"));

        String json = JsonUtils.toJsonString(Map.of("id", 2));
        assertEquals(2, ((Map<?, ?>) JsonUtils.parseObject(json, Map.class)).get("id"));
        assertEquals(2, JsonUtils.parseObject(json,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                }).get("id"));
        assertThrows(RuntimeException.class, () -> JsonUtils.parseObject("{broken", Map.class));

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        JsonUtils.init(mapper);
        assertEquals(3, ((Map<?, ?>) JsonUtils.parseObject("{\"id\":3}", Map.class)).get("id"));
    }

    @Test
    void sqlUtils_handlesBlankUrlEncodedAndFragmentPayloads() {
        assertFalse(SqlUtils.checkSqlInjection(null));
        assertFalse(SqlUtils.checkSqlInjection(""));
        assertFalse(SqlUtils.checkSqlInjection("   "));
        assertFalse(SqlUtils.checkSqlFragment(null));
        assertTrue(SqlUtils.checkSqlInjection("%27"));
        assertTrue(SqlUtils.checkSqlInjection("%3d1%27"));
        assertTrue(SqlUtils.checkSqlInjection("a';--"));
        assertTrue(SqlUtils.checkSqlFragment("1 union select password from user"));
        assertTrue(SqlUtils.checkSqlFragment("id > 0 /*x*/"));
        assertTrue(SqlUtils.checkSqlFragment("1; drop table t_user"));
        assertFalse(SqlUtils.checkSqlFragment("select id from t_dept where id < 3"));
    }

    @Test
    void one2OneComparator_sortsAscendingDescendingAndHandlesEdgeCases() {
        TestEntities.UserDO younger = new TestEntities.UserDO();
        younger.setAge(18);
        younger.setName("alice");
        TestEntities.UserDO older = new TestEntities.UserDO();
        older.setAge(30);
        older.setName("bob");

        TestEntities.UserCriteria criteria = new TestEntities.UserCriteria();
        criteria.setOrderBy("age@asc");
        Comparator<TestEntities.UserDO> asc =
                new One2OneObjectsComparator<TestEntities.UserDO, TestEntities.UserCriteria>(criteria) {
                };
        assertTrue(asc.compare(younger, older) < 0);
        assertTrue(asc.compare(younger, younger) == 0);

        criteria.setOrderBy("age@desc");
        Comparator<TestEntities.UserDO> desc =
                new One2OneObjectsComparator<TestEntities.UserDO, TestEntities.UserCriteria>(criteria) {
                };
        assertTrue(desc.compare(younger, older) > 0);

        criteria.setOrderBy("name@asc");
        Comparator<TestEntities.UserDO> byName =
                new One2OneObjectsComparator<TestEntities.UserDO, TestEntities.UserCriteria>(criteria) {
                };
        assertTrue(byName.compare(younger, older) < 0);

        criteria.setOrderBy("unknownField@asc");
        Comparator<TestEntities.UserDO> unknown =
                new One2OneObjectsComparator<TestEntities.UserDO, TestEntities.UserCriteria>(criteria) {
                };
        assertEquals(0, unknown.compare(younger, older));

        Comparator<TestEntities.UserDO> noCriteria =
                new One2OneObjectsComparator<TestEntities.UserDO, TestEntities.UserCriteria>(null) {
                };
        assertEquals(0, noCriteria.compare(younger, older));
    }
}
