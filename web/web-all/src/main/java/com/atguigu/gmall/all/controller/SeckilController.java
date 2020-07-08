package com.atguigu.gmall.all.controller;

import com.atguigu.gmall.activity.client.ActivityFeignClient;
import com.atguigu.gmall.common.result.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * @author Administrator
 * @create 2020-07-04 2:59
 */
@Controller
public class SeckilController {

    @Autowired
    private ActivityFeignClient activityFeignClient;

    /**
     * 秒杀列表
     * @param model
     * @return
     * // http://activity.gmall.com/seckill.html
     */
    @GetMapping("seckill.html")
    public String index(Model model) {
        // 页面需要后台存储一个list 集合
        Result result = activityFeignClient.findAll();
        model.addAttribute("list", result.getData());
        return "seckill/index";
    }

    /**
     * 秒杀详情
     * @param skuId
     * @param model
     * @return
     */
    @GetMapping("seckill/{skuId}.html")
    public String getItem(@PathVariable Long skuId, Model model){
        // 获取商品详情通过skuId 查询skuInfo
        Result result = activityFeignClient.getSeckillGoods(skuId);
        // 页面需要在后台存储一个item 对象
        model.addAttribute("item", result.getData());
        return "seckill/item";
    }

    /**
     * 秒杀排队
     * @param skuId
     * @param skuIdStr
     * @param request
     * @return
     */
    @GetMapping("seckill/queue.html")
    public String queue(@RequestParam(name = "skuId") Long skuId,
                        @RequestParam(name = "skuIdStr") String skuIdStr,
                        HttpServletRequest request){
        request.setAttribute("skuId", skuId);
        request.setAttribute("skuIdStr", skuIdStr);
        return "seckill/queue";
    }

    /**
     * 秒杀确认订单
     * @param model
     * @return
     */
    @GetMapping("seckill/trade.html")
    public String trade(Model model){
        Result<Map<String, Object>> result = activityFeignClient.trade();
        if (result.isOk()){
            //下单正常
            model.addAllAttributes(result.getData());
            return "seckill/trade";
        }else {
            //下单错误
            model.addAttribute("message",result.getMessage());
            return "seckill/fail";
        }
    }
}
