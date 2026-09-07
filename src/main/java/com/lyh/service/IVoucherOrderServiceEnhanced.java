package com.lyh.service;

import com.lyh.entity.VoucherOrder;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderServiceEnhanced extends IVoucherOrderService {
    void createOrder(VoucherOrder voucherOrder);
}
