package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.conditions.update.UpdateChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IShopService;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Collections;
import java.util.Map;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 团购下单 / 支付 / 取消 核心逻辑单元测试。
 */
class VoucherOrderServiceImplTest {

    private static final Long TEST_USER_ID = 999999L;
    private static final Long TEST_VOUCHER_ID = 88888L;
    private static final Long TEST_ORDER_ID = 555555L;

    @Mock private ISeckillVoucherService seckillVoucherService;
    @Mock private IVoucherService voucherService;
    @Mock private IShopService shopService;
    @Mock private RedisIdWorker redisIdWorker;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private VoucherOrderMapper voucherOrderMapper;

    @Spy
    @InjectMocks
    private VoucherOrderServiceImpl voucherOrderService;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(voucherOrderService, "baseMapper", voucherOrderMapper);
        UserDTO user = new UserDTO();
        user.setId(TEST_USER_ID);
        UserHolder.saveUser(user);
        when(redisIdWorker.nextId(anyString())).thenReturn(TEST_ORDER_ID);
    }

    @AfterEach
    void tearDown() throws Exception {
        UserHolder.removeUser();
        mocks.close();
    }

    private Voucher voucher(Integer type, Integer status) {
        return new Voucher().setId(TEST_VOUCHER_ID).setType(type).setStatus(status);
    }

    private VoucherOrder pendingOrder() {
        return new VoucherOrder()
                .setId(TEST_ORDER_ID)
                .setUserId(TEST_USER_ID)
                .setVoucherId(TEST_VOUCHER_ID)
                .setStatus(1)
                .setCreateTime(LocalDateTime.now());
    }

    private void stubOrder(VoucherOrder order) {
        doReturn(order).when(voucherOrderService).getById(TEST_ORDER_ID);
    }

    // ---------- 下单 ----------

    @Test
    void createOrder_voucherNotExist_fails() {
        when(voucherService.getById(TEST_VOUCHER_ID)).thenReturn(null);
        assertFalse(voucherOrderService.createOrder(TEST_VOUCHER_ID).getSuccess());
        verify(voucherOrderMapper, never()).insert(any());
    }

    @Test
    void createOrder_seckillVoucher_fails() {
        when(voucherService.getById(TEST_VOUCHER_ID)).thenReturn(voucher(1, 1));
        assertFalse(voucherOrderService.createOrder(TEST_VOUCHER_ID).getSuccess());
    }

    @Test
    void createOrder_normalVoucher_createsPendingOrder() {
        when(voucherService.getById(TEST_VOUCHER_ID)).thenReturn(voucher(0, 1));
        var result = voucherOrderService.createOrder(TEST_VOUCHER_ID);
        assertTrue(result.getSuccess());
        // 订单 id 以字符串返回，避免雪花 ID 超出 JS 安全整数导致精度丢失
        assertEquals(String.valueOf(TEST_ORDER_ID), result.getData());
        ArgumentCaptor<VoucherOrder> cap = ArgumentCaptor.forClass(VoucherOrder.class);
        verify(voucherOrderMapper).insert(cap.capture());
        assertEquals(1, cap.getValue().getStatus());
        assertEquals(TEST_USER_ID, cap.getValue().getUserId());
    }

    // ---------- 支付 ----------

    @Test
    void payOrder_orderNotExist_fails() {
        stubOrder(null);
        assertFalse(voucherOrderService.payOrder(TEST_ORDER_ID, 1).getSuccess());
    }

    @Test
    void payOrder_notOwner_fails() {
        stubOrder(new VoucherOrder().setId(TEST_ORDER_ID).setUserId(1L).setStatus(1));
        assertFalse(voucherOrderService.payOrder(TEST_ORDER_ID, 1).getSuccess());
    }

    @Test
    void payOrder_pending_success() {
        stubOrder(pendingOrder());
        var result = voucherOrderService.payOrder(TEST_ORDER_ID, 2);
        assertTrue(result.getSuccess());
        ArgumentCaptor<VoucherOrder> cap = ArgumentCaptor.forClass(VoucherOrder.class);
        verify(voucherOrderMapper).updateById(cap.capture());
        assertEquals(2, cap.getValue().getStatus());
        assertEquals(2, cap.getValue().getPayType());
        assertNotNull(cap.getValue().getPayTime());
    }

    // ---------- 取消（秒杀回补库存） ----------

    @Test
    void cancelOrder_pendingSeckill_releasesStock() {
        stubOrder(pendingOrder());
        when(voucherService.getById(TEST_VOUCHER_ID)).thenReturn(voucher(1, 1));

        @SuppressWarnings("unchecked")
        UpdateChainWrapper<SeckillVoucher> chain = mock(UpdateChainWrapper.class);
        when(chain.setSql(anyString())).thenReturn(chain);
        when(chain.eq(anyString(), any())).thenReturn(chain);
        when(chain.update()).thenReturn(true);
        when(seckillVoucherService.update()).thenReturn(chain);

        @SuppressWarnings("unchecked")
        ValueOperations<String, String> vo = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(vo);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> so = mock(SetOperations.class);
        when(stringRedisTemplate.opsForSet()).thenReturn(so);

        var result = voucherOrderService.cancelOrder(TEST_ORDER_ID);
        assertTrue(result.getSuccess());

        ArgumentCaptor<VoucherOrder> cap = ArgumentCaptor.forClass(VoucherOrder.class);
        verify(voucherOrderMapper).updateById(cap.capture());
        assertEquals(4, cap.getValue().getStatus());
        verify(vo).increment("seckill:stock:" + TEST_VOUCHER_ID);
        verify(so).remove("seckill:order:" + TEST_VOUCHER_ID, TEST_USER_ID.toString());
    }

    @Test
    void cancelOrder_pendingNormal_noStockRelease() {
        stubOrder(pendingOrder());
        when(voucherService.getById(TEST_VOUCHER_ID)).thenReturn(voucher(0, 1));

        var result = voucherOrderService.cancelOrder(TEST_ORDER_ID);
        assertTrue(result.getSuccess());
        verify(stringRedisTemplate, never()).opsForValue();
        verify(stringRedisTemplate, never()).opsForSet();
    }

    @Test
    void seckillStatus_returnsTrueWhenOrderExists() {
        @SuppressWarnings("unchecked")
        com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper<VoucherOrder> chain =
                mock(com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper.class);
        when(chain.eq(anyString(), any())).thenReturn(chain);
        when(chain.count()).thenReturn(1);
        doReturn(chain).when(voucherOrderService).query();

        Result r = voucherOrderService.seckillStatus(TEST_VOUCHER_ID);
        assertTrue(r.getSuccess());
        assertTrue((Boolean) r.getData());
    }

    @Test
    void seckillStatus_returnsFalseWhenNoOrder() {
        @SuppressWarnings("unchecked")
        com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper<VoucherOrder> chain =
                mock(com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper.class);
        when(chain.eq(anyString(), any())).thenReturn(chain);
        when(chain.count()).thenReturn(0);
        doReturn(chain).when(voucherOrderService).query();

        Result r = voucherOrderService.seckillStatus(TEST_VOUCHER_ID);
        assertTrue(r.getSuccess());
        assertFalse((Boolean) r.getData());
    }

    @Test
    void queryMyOrders_returnsOrderIdAsString() {
        @SuppressWarnings("unchecked")
        com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper<VoucherOrder> chain =
                mock(com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper.class);
        when(chain.eq(anyString(), any())).thenReturn(chain);
        when(chain.orderByDesc(anyString())).thenReturn(chain);
        VoucherOrder order = new VoucherOrder()
                .setId(601234567890123456L)
                .setUserId(TEST_USER_ID)
                .setVoucherId(TEST_VOUCHER_ID)
                .setStatus(1);
        when(chain.list()).thenReturn(Collections.singletonList(order));
        doReturn(chain).when(voucherOrderService).query();
        when(voucherService.getById(TEST_VOUCHER_ID))
                .thenReturn(new Voucher().setId(TEST_VOUCHER_ID).setTitle("冬日双人餐"));

        Result r = voucherOrderService.queryMyOrders();
        assertTrue(r.getSuccess());
        java.util.List<Map<String, Object>> list = (java.util.List<Map<String, Object>>) r.getData();
        assertEquals(1, list.size());
        // id 必须是字符串，避免雪花 ID 在 JS 丢精度
        assertEquals("601234567890123456", list.get(0).get("id"));
    }

    // ---------- 退款（已支付且未核销） ----------

    private VoucherOrder paidOrder() {
        return new VoucherOrder()
                .setId(TEST_ORDER_ID)
                .setUserId(TEST_USER_ID)
                .setVoucherId(TEST_VOUCHER_ID)
                .setStatus(2)
                .setCreateTime(LocalDateTime.now())
                .setPayTime(LocalDateTime.now());
    }

    @SuppressWarnings("unchecked")
    private UpdateChainWrapper<VoucherOrder> stubUpdate(boolean success) {
        UpdateChainWrapper<VoucherOrder> chain = mock(UpdateChainWrapper.class);
        when(chain.set(anyString(), any())).thenReturn(chain);
        when(chain.eq(anyString(), any())).thenReturn(chain);
        when(chain.update()).thenReturn(success);
        doReturn(chain).when(voucherOrderService).update();
        return chain;
    }

    @Test
    void refundOrder_orderNotExist_fails() {
        stubOrder(null);
        assertFalse(voucherOrderService.refundOrder(TEST_ORDER_ID).getSuccess());
    }

    @Test
    void refundOrder_notOwner_fails() {
        stubOrder(new VoucherOrder().setId(TEST_ORDER_ID).setUserId(1L).setStatus(2));
        assertFalse(voucherOrderService.refundOrder(TEST_ORDER_ID).getSuccess());
    }

    @Test
    void refundOrder_notPaid_fails() {
        stubOrder(pendingOrder()); // status=1 待支付
        assertFalse(voucherOrderService.refundOrder(TEST_ORDER_ID).getSuccess());
        verify(voucherOrderService, never()).update();
    }

    @Test
    void refundOrder_paidSeckill_releasesStock() {
        stubOrder(paidOrder());
        when(voucherService.getById(TEST_VOUCHER_ID)).thenReturn(voucher(1, 1));
        stubUpdate(true);

        @SuppressWarnings("unchecked")
        UpdateChainWrapper<SeckillVoucher> chain = mock(UpdateChainWrapper.class);
        when(chain.setSql(anyString())).thenReturn(chain);
        when(chain.eq(anyString(), any())).thenReturn(chain);
        when(chain.update()).thenReturn(true);
        when(seckillVoucherService.update()).thenReturn(chain);

        @SuppressWarnings("unchecked")
        ValueOperations<String, String> vo = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(vo);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> so = mock(SetOperations.class);
        when(stringRedisTemplate.opsForSet()).thenReturn(so);

        var result = voucherOrderService.refundOrder(TEST_ORDER_ID);
        assertTrue(result.getSuccess());
        verify(vo).increment("seckill:stock:" + TEST_VOUCHER_ID);
        verify(so).remove("seckill:order:" + TEST_VOUCHER_ID, TEST_USER_ID.toString());
    }

    @Test
    void refundOrder_paidNormal_noStockRelease() {
        stubOrder(paidOrder());
        when(voucherService.getById(TEST_VOUCHER_ID)).thenReturn(voucher(0, 1));
        stubUpdate(true);

        var result = voucherOrderService.refundOrder(TEST_ORDER_ID);
        assertTrue(result.getSuccess());
        verify(stringRedisTemplate, never()).opsForValue();
        verify(stringRedisTemplate, never()).opsForSet();
    }

    @Test
    void refundOrder_statusChangedConcurrently_fails() {
        stubOrder(paidOrder());
        stubUpdate(false); // CAS 更新 0 行：并发下状态已变化（已核销/已退款）
        assertFalse(voucherOrderService.refundOrder(TEST_ORDER_ID).getSuccess());
        verify(stringRedisTemplate, never()).opsForValue();
    }
}
