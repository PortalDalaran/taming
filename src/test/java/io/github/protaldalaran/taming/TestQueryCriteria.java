package io.github.protaldalaran.taming;

import io.github.portaldalaran.taming.utils.BuildUtils;
import io.github.portaldalaran.taming.utils.QueryConstants;

public class TestQueryCriteria {
    public static void main(String[] args) {
//        Student student = new Student();
//        student.setAge(1);
//        student.setName("张三");
//        student.addQueryCriteriaParam(Student::getName, QueryConstants.EQ,"name");
        System.out.println( BuildUtils.getFieldName(Student::getName));;
    }
}
