package com.atguigu.gmall.all.controller;

import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.order.client.OrderFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.http.HttpServletRequest;

/**
 * @author Administrator
 * @create 2020-06-30 1:25
 */
@Controller
public class PaymentController {

    @Autowired
    private OrderFeignClient orderFeignClient;

    /**
     * 支付页
     * @param request
     * @return
     */
    @GetMapping("pay.html")
    public String pay(HttpServletRequest request){
        String orderId = request.getParameter("orderId");
        OrderInfo orderInfo = orderFeignClient.getOrderInfo(Long.parseLong(orderId));

        // 保存一个orderInfo 对象
        request.setAttribute("orderInfo",orderInfo);
        return "payment/pay";
    }

    /**
     * 支付成功页
     * @return
     * 支付成功之后回调地址
     */
    @GetMapping("pay/success.html")
    public String success(){
        return "payment/success";
    }


}
