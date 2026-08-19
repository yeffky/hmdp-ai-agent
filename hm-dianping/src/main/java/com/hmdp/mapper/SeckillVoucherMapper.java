package com.hmdp.mapper;

import com.hmdp.entity.SeckillVoucher;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 * 秒杀优惠券表，与优惠券是一对一关系 Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2022-01-04
 */
public interface SeckillVoucherMapper extends BaseMapper<SeckillVoucher> {

    /** 查询未过期且上架的秒杀券，预热时仅初始化尚未开始的库存 key。 */
    @Select("SELECT sv.voucher_id, sv.stock, sv.create_time, sv.begin_time, sv.end_time, sv.update_time " +
            "FROM tb_seckill_voucher sv " +
            "JOIN tb_voucher v ON v.id = sv.voucher_id " +
            "WHERE v.type = 1 AND v.status = 1 AND sv.end_time > NOW()")
    List<SeckillVoucher> listActiveForWarmup();

}
