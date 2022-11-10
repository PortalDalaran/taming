package io.github.portaldalaran.taming.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 返回的关联表字段
 * Associated Table Fields Returned
 * @author aohee@163.com
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SelectAssociationFields {
    private String entityName;

    private List<String> fieldNames;
}
