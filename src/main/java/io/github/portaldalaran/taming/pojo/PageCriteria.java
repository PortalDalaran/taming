package io.github.portaldalaran.taming.pojo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.io.Serializable;

/**
 * 查询条件
 *
 * @author aohee@163.com
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PageCriteria extends QueryCriteria implements Serializable {

    private static final Integer PAGE_NO = 1;
    private static final Integer PAGE_SIZE = 15;

    /**
     * 页码，从 1 开始
     *
     */
    private Integer pageNo = PAGE_NO;


    /**
     * 每页条数，最大值为 100
     * max 100
     */
    private Integer pageSize = PAGE_SIZE;

    public PageParam getPageParam() {
        return new PageParam(this.pageNo, this.pageSize);
    }
}
