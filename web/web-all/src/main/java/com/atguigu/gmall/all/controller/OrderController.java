package com.atguigu.gmall.all.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.order.client.OrderFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

/**
 * @author mqx
 * @date 2020/6/24 15:50
 */
@Controller
public class OrderController {

    @Autowired
    private OrderFeignClient orderFeignClient;


    /**
     * 确认订单
     * @param model
     * @return
     * // http://order.gmall.com/trade.html
     */
    @GetMapping("trade.html")
    public String trade(Model model){
        Result<Map<String, Object>> result = orderFeignClient.trade();
        // 存储数据，给前台页面使用
        model.addAllAttributes(result.getData());
        // 订单页面
        return "order/trade";
    }
}
