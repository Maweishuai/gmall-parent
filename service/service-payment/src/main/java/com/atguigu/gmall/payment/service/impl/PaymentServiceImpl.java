package com.atguigu.gmall.payment.service.impl;

import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.model.enums.PaymentStatus;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.payment.PaymentInfo;
import com.atguigu.gmall.payment.mapper.PaymentInfoMapper;
import com.atguigu.gmall.payment.service.PaymentService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;

/**
 *
 * @author Administrator
 * @create 2020-06-30 1:36
 */
@Service
public class PaymentServiceImpl implements PaymentService {

    @Autowired
    private PaymentInfoMapper paymentInfoMapper;

    @Autowired
    private RabbitService rabbitService;

    /**
     * 保存交易记录
     * @param orderInfo
     * @param paymentType 支付类型（1：微信 2：支付宝）
     */
    @Override
    public void savePaymentInfo(OrderInfo orderInfo, String paymentType) {
        QueryWrapper<PaymentInfo> paymentInfoQueryWrapper = new QueryWrapper<>();
        paymentInfoQueryWrapper.eq("order_id",orderInfo.getId());
        paymentInfoQueryWrapper.eq("payment_type",paymentType);

        Integer count = paymentInfoMapper.selectCount(paymentInfoQueryWrapper);
        if (count>0){
            return;
        }
        // 保存交易记录
        // 创建一个对象
        PaymentInfo paymentInfo = new PaymentInfo();
        // 给对象赋值
        paymentInfo.setCreateTime(new Date());
        paymentInfo.setOrderId(orderInfo.getId());
        paymentInfo.setPaymentType(paymentType);
        paymentInfo.setOutTradeNo(orderInfo.getOutTradeNo());
        paymentInfo.setPaymentStatus(PaymentStatus.UNPAID.name());
        paymentInfo.setSubject(orderInfo.getTradeBody());
        paymentInfo.setTotalAmount(orderInfo.getTotalAmount());

        //保存交易数据
        paymentInfoMapper.insert(paymentInfo);
    }

    /**
     * 支付宝异步回调--获取交易记录信息
     * @param outTradeNo
     * @param paymentType
     * 根据out_trade_no 以及支付方式查询交易记录
     * @return
     */
    @Override
    public PaymentInfo getPaymentInfo(String outTradeNo, String paymentType) {
        QueryWrapper<PaymentInfo> paymentInfoQueryWrapper = new QueryWrapper<>();
        paymentInfoQueryWrapper.eq("out_trade_no",outTradeNo).eq("payment_type",paymentType);
        PaymentInfo paymentInfo = paymentInfoMapper.selectOne(paymentInfoQueryWrapper);

        //返回交易记录信息
        return paymentInfo;
    }


    /**
     * 支付宝异步回调-支付成功
     * @param outTradeNo
     * @param paymentType
     * @param paramMap
     */
    @Override
    public void paySuccess(String outTradeNo, String paymentType, Map<String, String> paramMap) {
        //需要获取到订单Id
        PaymentInfo paymentInfo = this.getPaymentInfo(outTradeNo, paymentType);

        //声明支付成功后的改变状态后的paymentInfoUpd数据
        PaymentInfo paymentInfoUpd  = new PaymentInfo();
        paymentInfoUpd.setPaymentStatus(PaymentStatus.PAID.name());
        paymentInfoUpd.setCallbackTime(new Date());
        // 更新支付宝的交易号，交易号在map 中
        paymentInfoUpd.setTradeNo(paramMap.get("trade_no"));
        paymentInfoUpd.setCallbackContent(paramMap.toString());

        this.updatePaymentInfo(outTradeNo,paymentInfoUpd);
        // 更新后表示交易成功！

        // 发送消息通知订单
        // 更新订单状态 订单Id 或者 outTradeNo
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_PAYMENT_PAY,MqConst.ROUTING_PAYMENT_PAY,paymentInfo.getOrderId());
    }

    /**
     * 支付宝异步回调-根据第三方交易编号，修改支付交易记录
     * @param outTradeNo
     * @param paymentInfoUpd
     */
    @Override
    public void updatePaymentInfo(String outTradeNo, PaymentInfo paymentInfoUpd) {
        // 根据第三方交易编号更新交易记录。
        QueryWrapper<PaymentInfo> paymentInfoQueryWrapper = new QueryWrapper<>();
        paymentInfoQueryWrapper.eq("out_trade_no",outTradeNo);
        // 第一个参数更新的内容，第二个参数更新的条件
        paymentInfoMapper.update(paymentInfoUpd,paymentInfoQueryWrapper);
    }

    /**
     * 关闭过期交易记录
     * @param orderId
     */
    @Override
    public void closePayment(Long orderId) {
        // 设置关闭交易记录的条件  118
        QueryWrapper<PaymentInfo> paymentInfoQueryWrapper = new QueryWrapper<>();
        paymentInfoQueryWrapper.eq("order_id",orderId);
        // 关闭交易
        // 先查询 paymentInfo 交易记录 select count(*) from payment_info where order_id = orderId
        Integer count = paymentInfoMapper.selectCount(paymentInfoQueryWrapper);
        // 如果当前的交易记录不存在，则不更新交易记录
        if (null == count || count.intValue()==0) return;// 说明这个订单没有交易记录
        // 否则要关闭
        // 在关闭支付宝交易之前。还需要关闭paymentInfo 改变paymentInfo状态为CLOSED
        // update payment_info set PaymentStatus = CLOSED where order_id = orderId
        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setPaymentStatus(PaymentStatus.ClOSED.name());
        paymentInfoMapper.update(paymentInfo,paymentInfoQueryWrapper);
    }
}
