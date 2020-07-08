package com.atguigu.gmall.payment.service;

import com.alipay.api.AlipayApiException;

public interface AlipayService {

    /**
     * 根据orderId获取Alipay
     * @param orderId
     * @return
     * @throws AlipayApiException
     */
    String createaliPay(Long orderId) throws AlipayApiException;

    /**
     * 发起退款
     * @param orderId
     * @return
     */
    boolean refund(Long orderId);

    /**
     * 关闭支付宝交易
     * @param orderId
     * @return
     */
    Boolean closePay(Long orderId);

    /**
     * 根据订单查询是否支付成功
     * @param orderId
     * @return
     */
    Boolean checkPayment(Long orderId);
}