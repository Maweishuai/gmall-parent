package com.atguigu.gmall.payment.service.impl;

import com.alibaba.fastjson.JSON;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.atguigu.gmall.model.enums.PaymentStatus;
import com.atguigu.gmall.model.enums.PaymentType;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.payment.PaymentInfo;
import com.atguigu.gmall.order.client.OrderFeignClient;
import com.atguigu.gmall.payment.config.AlipayConfig;
import com.atguigu.gmall.payment.service.AlipayService;
import com.atguigu.gmall.payment.service.PaymentService;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMapping;
import springfox.documentation.spring.web.json.Json;

import java.util.HashMap;

/**
 * @author Administrator
 * @create 2020-06-30 1:51
 */
@Service
public class AlipayServiceImpl implements AlipayService {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrderFeignClient orderFeignClient;

    @Autowired
    private AlipayClient alipayClient;

    /**
     * 根据orderId获取Alipay
     *
     * @param orderId
     * @return
     * @throws AlipayApiException
     */
    @Override
    public String createaliPay(Long orderId) throws AlipayApiException {
        //获取订单对象
        OrderInfo orderInfo = orderFeignClient.getOrderInfo(orderId);
        //保存交易记录
        paymentService.savePaymentInfo(orderInfo, PaymentType.ALIPAY.name());
        //生成二维码
        // 创建API对应的request
        AlipayTradePagePayRequest alipayRequest = new AlipayTradePagePayRequest();
        //同步回调
//        alipayRequest.setReturnUrl("http://domain.com/CallBack/return_url.jsp");
        alipayRequest.setReturnUrl(AlipayConfig.return_payment_url);
        //异步回调
//        alipayRequest.setNotifyUrl("http://domain.com/CallBack/notify_url.jsp");
        alipayRequest.setNotifyUrl(AlipayConfig.notify_payment_url);
        //声明一个集合
        HashMap<String, Object> map = new HashMap<>();
        map.put("out_trade_no", orderInfo.getOutTradeNo());
        map.put("product_code", "FAST_INSTANT_TRADE_PAY");
        map.put("total_amount", orderInfo.getTotalAmount());
        map.put("subject", "Maweishuai");
        //将map转化为json字符串
        alipayRequest.setBizContent(JSON.toJSONString(map));
        //调用SDK生成表单;直接将完整的表单html返回
        return alipayClient.pageExecute(alipayRequest).getBody();
    }

    /**
     * 发起退款
     *
     * @param orderId
     * @return
     */
    @Override
    public boolean refund(Long orderId) {
        // 获取订单对象
        OrderInfo orderInfo = orderFeignClient.getOrderInfo(orderId);
        // 创建API对应的request
        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();

        // 声明一个map 集合来存储数据
        HashMap<String, Object> map = new HashMap<>();
        map.put("out_trade_no",orderInfo.getOutTradeNo());
        map.put("refund_amount",orderInfo.getTotalAmount());
        map.put("refund_reason","空调不够凉！");
        // 支付的时候 支付的1块钱。
        // 退款能否退0.5 元？
        // map.put("out_request_no","HZ01RF001");
        // 将map转化为json字符串
        request.setBizContent(JSON.toJSONString(map));

        AlipayTradeRefundResponse response=null;
        try {
            response = alipayClient.execute(request);
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }

        //判断退款是否成功
        if (response.isSuccess()) {
            //如果成功了
            // 更新交易记录 ： 关闭
            PaymentInfo paymentInfo = new PaymentInfo();
            paymentInfo.setPaymentStatus(PaymentStatus.ClOSED.name());
            // 根据 out_trade_no 更新paymentInfo
            paymentService.updatePaymentInfo(orderInfo.getOutTradeNo(), paymentInfo);
            System.out.println("退款成功");
            return true;
        } else {
            System.out.println("退款失败");
            return false;
        }
    }

    /**
     * 关闭支付宝交易
     * @param orderId
     * @return
     */
    @SneakyThrows
    @Override
    public Boolean closePay(Long orderId) {
        //获取订单信息
        // out_trade_no 是orderInfo中的OutTradeNo 也是PaymentInfo中OutTradeNo
        OrderInfo orderInfo = orderFeignClient.getOrderInfo(orderId);
        // 创建API对应的request
        // 关闭支付宝交易 https://opendocs.alipay.com/apis/api_1/alipay.trade.close
        // AlipayClient alipayClient = new DefaultAlipayClient("https://openapi.alipay.com/gateway.do","app_id","your private_key","json","GBK","alipay_public_key","RSA2");
        AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();

        //声明一个集合用来存储
        HashMap<String, Object> map = new HashMap<>();
        // map.put("trade_no",paymentInfo.getTradeNo()); // 从paymentInfo 中获取！
        map.put("out_trade_no",orderInfo.getOutTradeNo());
        map.put("operator_id","YX01");//可选
        //将map转化为json字符串
        request.setBizContent(JSON.toJSONString(map));
        // 准备执行
        AlipayTradeCloseResponse response = null;
        try {
            response = alipayClient.execute(request);
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
        if(response.isSuccess()){
            System.out.println("调用成功");
            return true;
        } else {
            System.out.println("调用失败");
            return false;
        }
    }

    /**
     * 根据订单查询是否支付成功
     * @param orderId
     * @return
     */
    @SneakyThrows
    @Override
    public Boolean checkPayment(Long orderId) {

        // 根据订单Id 查询订单信息
        // out_trade_no 是orderInfo中的OutTradeNo 也是PaymentInfo中OutTradeNo
        OrderInfo orderInfo = orderFeignClient.getOrderInfo(orderId);

        // 创建API对应的request
        // AlipayClient alipayClient = new DefaultAlipayClient("https://openapi.alipay.com/gateway.do","app_id","your private_key","json","GBK","alipay_public_key","RSA2");
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();

        //声明一个集合用来存储
        HashMap<String, Object> map = new HashMap<>();
        // map.put("trade_no",paymentInfo.getTradeNo()); // 从paymentInfo 中获取！
        map.put("out_trade_no",orderInfo.getOutTradeNo());
        //将map转化为json字符串
        request.setBizContent(JSON.toJSONString(map));
        // 准备执行
        AlipayTradeQueryResponse response = null;
        try {
            response = alipayClient.execute(request);
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
        if(response.isSuccess()){
            System.out.println("调用成功");
            return true;
        } else {
            System.out.println("调用失败");
            return false;
        }
    }
}
