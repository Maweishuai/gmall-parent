package com.atguigu.gmall.item.service;

import java.util.Map;

/**
 * @author Administrator
 * @create 2020-06-14 16:04
 */
public interface ItemService {

    /**
     * 获取商品详情的Map信息
     * 1. Sku基本信息
     * 2，Sku图片信息
     * 3，Sku分类信息
     * 4，Sku销售属性相关信息
     * 5，Sku价格信息（平台可以单独修改价格，sku后续会放入缓存，为了回显最新价格，所以单独获取）
     * @param skuId
     * @return
     */
    Map<String,Object> getBySkuId(Long skuId);
}
