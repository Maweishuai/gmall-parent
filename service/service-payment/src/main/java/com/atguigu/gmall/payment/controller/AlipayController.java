package com.atguigu.gmall.payment.controller;

import com.alipay.api.AlipayApiException;
import com.alipay.api.internal.util.AlipaySignature;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.enums.PaymentStatus;
import com.atguigu.gmall.model.enums.PaymentType;
import com.atguigu.gmall.model.payment.PaymentInfo;
import com.atguigu.gmall.payment.config.AlipayConfig;
import com.atguigu.gmall.payment.service.AlipayService;
import com.atguigu.gmall.payment.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.util.Map;

import static com.alipay.api.AlipayConstants.CHARSET;
import static com.alipay.api.AlipayConstants.SIGN_TYPE;

/**
 * @author Administrator
 * @create 2020-06-30 1:45
 */
@Controller
@RequestMapping("/api/payment/alipay")
public class AlipayController {

    @Autowired
    private AlipayService alipayService;

    @Autowired
    private PaymentService paymentService;

    /**
     * 提交订单
     *
     * @param orderId
     * @param response
     * @return
     */
    @RequestMapping("submit/{orderId}")
    @ResponseBody
    public String submitOrder(@PathVariable(value = "orderId") Long orderId, HttpServletResponse response) {
        String from = "";
        try {
            from = alipayService.createaliPay(orderId);
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
        return from;
    }

    /**
     * 支付宝同步回调
     *
     * @return
     */
    @RequestMapping("callback/return")
    public String callbackReturn() {
        // 同步回调给用户展示信息
        return "redirect:" + AlipayConfig.return_order_url;
    }

    /**
     * 支付宝异步回调  必须使用内网穿透
     *
     * @param paramMap
     * @return
     */
    @RequestMapping("callback/notify")
    @ResponseBody
    public String callbackNotify(@RequestParam Map<String, String> paramMap) {

        //从支付宝官网复制来的demo

        //将异步通知中收到的所有参数都存放到map中
//        Map<String, String> paramsMap = ...
        String trade_status = paramMap.get("trade_status"); //交易状态
        String out_trade_no = paramMap.get("out_trade_no"); //商户订单号
        String app_id = paramMap.get("app_id");             //开发者的app_id
        String total_amount = paramMap.get("total_amount"); //订单金额

        boolean signVerfied = false;
        try {
            //AlipaySignature 此方法用于调用SDK验证签名
            signVerfied = AlipaySignature.rsaCheckV1(paramMap, AlipayConfig.alipay_public_key, AlipayConfig.charset, AlipayConfig.sign_type);
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
        if (signVerfied) {
            // TODO 验签成功后，按照支付结果异步通知中的描述，对支付结果中的业务内容进行二次校验，校验成功后在response中返回success并继续商户自身业务处理，校验失败返回failure
            // 在支付宝的业务通知中，只有交易通知状态为 TRADE_SUCCESS 或 TRADE_FINISHED 时，支付宝才会认定为买家付款成功。
            if ("TRADE_SUCCESS".equals(trade_status) || "TRADE_FINISHED".equals(trade_status)) {
                // 还需要做一个判断，虽然你支付的状态判断完了，\
                // 但是有没有这么一种可能，你的交易记录中的支付状态已经变成付款了，或者是关闭了，那么应该返回验签失败！
                // 通过outTradeNo来查询paymentInfo数据
                PaymentInfo paymentInfo = paymentService.getPaymentInfo(out_trade_no, PaymentType.ALIPAY.name());
                //也就是交易记录表中 PAID 或者 CLOSE ，获取交易记录中的支付状态
                if (paymentInfo.getPaymentStatus().equals(PaymentStatus.PAID.name())
                        || paymentInfo.getPaymentStatus().equals(PaymentStatus.ClOSED.name())) {
                    return "failure";
                }
//                  上面只根据out_trade_no进行了验签，还可以用以下条件
//                  商户需要验证该通知数据中的 out_trade_no 是否为商户系统中创建的订单号；
//                  判断 total_amount 是否确实为该订单的实际金额（即商户订单创建时的金额）；
//                  验证 app_id 是否为该商户本身。
//                if (out_trade_no.equals(paymentInfo.getOutTradeNo())
//						&& total_amount == paymentInfo.getTotalAmount()
//						&& app_id.equals(AlipayConfig.appId)){
//                    // 表示支付成功，此时才会更新交易记录的状态
//                    paymentService.paySuccess(out_trade_no,PaymentType.ALIPAY.name(),paramMap);
//                    return "success";
//                }
                // 正常的支付成功，我们应该更新交易记录状态
                paymentService.paySuccess(out_trade_no, PaymentType.ALIPAY.name(), paramMap);

                return "success";
            }
        } else {
            // TODO 验签失败则记录异常日志，并在response中返回failure.
            return "failure";
        }
        return "failure";
    }

    /**
     * 发起退款
     *
     * @param orderId
     * @return http://localhost:8205/api/payment/alipay/refund/20
     */
    @RequestMapping("refund/{orderId}")
    @ResponseBody
    public Result refund(@PathVariable(value = "orderId") Long orderId) {
        // 调用退款接口
        boolean flag = alipayService.refund(orderId);

        return Result.ok(flag);
    }

    /**
     * 关闭支付宝交易
     * @param orderId
     * @return
     */
    @GetMapping("closePay/{orderId}")
    @ResponseBody
    public Boolean closePay(@PathVariable Long orderId){
        Boolean falg = alipayService.closePay(orderId);
        return falg;
    }

    /**
     * 查看是否有交易记录
     * 根据订单查询是否支付成功
     * @param orderId
     * @return
     */
    @RequestMapping("checkPayment/{orderId}")
    @ResponseBody
    public Boolean checkPayment(@PathVariable Long orderId){
        // 调用退款接口
        boolean flag = alipayService.checkPayment(orderId);
        return flag;
    }

    /**
     * 关闭订单之前-获取交易记录信息
     * 通过OutTradeNo 查询paymentInfo
     * @param outTradeNo
     * @return
     */
    @GetMapping("getPaymentInfo/{outTradeNo}")
    @ResponseBody
    public PaymentInfo getPaymentInfo(@PathVariable String outTradeNo){
        // 通过交易编号和支付方式查询paymentInfo
        PaymentInfo paymentInfo = paymentService.getPaymentInfo(outTradeNo, PaymentType.ALIPAY.name());
        //判断交易记录是否存在
        if (null!=paymentInfo){
            return paymentInfo;
        }
        return null;
    }
}
