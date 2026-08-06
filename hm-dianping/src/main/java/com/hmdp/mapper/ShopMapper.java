package com.hmdp.mapper;

import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Update;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface ShopMapper extends BaseMapper<Shop> {

    /**
     * 按用户评论均分批量重算店铺评分（score = 均分×10）。
     * 仅重算有评论的店铺；无评论的保留原分。
     */
    @Update("UPDATE tb_shop s " +
            "SET s.score = COALESCE((SELECT ROUND(AVG(rating) * 10) FROM tb_shop_comment c WHERE c.shop_id = s.id), s.score) " +
            "WHERE s.id IN (SELECT shop_id FROM tb_shop_comment GROUP BY shop_id)")
    int recalcScores();
}
