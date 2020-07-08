package com.atguigu.gmall.all.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.item.client.ItemFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

/**
 * @author mqx
 * @date 2020/6/15 10:25
 */
@Controller
public class ItemController {

    // 注入远程调用接口
    @Autowired
    private ItemFeignClient itemFeignClient;
    // 分析这个控制器如何编写？

    @RequestMapping("{skuId}.html")
    public String getItem(@PathVariable Long skuId, Model model){
        Result<Map> result = itemFeignClient.getItem(skuId);
        // 将result 中map 全部保存到作用域中！
        model.addAllAttributes(result.getData());
        // 返回商品详情页面,页面需要做数据渲染，将数据保存起来
        return "item/index";
    }

}
