package com.atguigu.gmall.item.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.item.service.ItemService;
import com.atguigu.gmall.list.client.ListFeignClient;
import com.atguigu.gmall.model.product.BaseCategoryView;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.model.product.SpuSaleAttr;
import com.atguigu.gmall.product.client.ProductFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * service-item模块从service-product-client模块获取数据并汇总数据
 * @author Administrator
 * @create 2020-06-14 16:07
 */
@Service
public class ItemServiceImpl implements ItemService {
    
    @Autowired
    private ProductFeignClient productFeignClient;

    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;

    @Autowired
    private ListFeignClient listFeignClient;
    
    @Override
    public Map<String, Object> getBySkuId(Long skuId) {

        Map<String, Object> resultMap = new HashMap<>();
        /**
         * 1. Sku基本信息
         * result.put("1",Sku基本信息);
         * 2，Sku图片信息
         * result.put("2",Sku图片信息);
         * 3，Sku分类信息
         * result.put("3",Sku分类信息);
         * 4，Sku销售属性相关信息
         * result.put("4",Sku销售属性相关信息);
         * 5，Sku价格信息
         * result.put("5",Sku价格信息);
         */

        // 异步编排 
        CompletableFuture<SkuInfo> skuInfoCompletableFuture  = CompletableFuture.supplyAsync(() -> {
            //根据skuId获取sku信息{包含skuImage}
            SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);
            //保存skuInfo信息{包含skuImage}
            resultMap.put("skuInfo", skuInfo);
            return skuInfo;
        },threadPoolExecutor);
        
        CompletableFuture<Void> spuSaleAttrListCompletableFuture= skuInfoCompletableFuture.thenAcceptAsync(skuInfo -> {
            //根据spuId，skuId 查询销售属性集合,销售属性-销售属性值回显并锁定
            List<SpuSaleAttr> spuSaleAttrListCheckBySku = productFeignClient.getSpuSaleAttrListCheckBySku(skuId, skuInfo.getSpuId());
            //保存销售属性及属性值数据
            resultMap.put("spuSaleAttrList", spuSaleAttrListCheckBySku);
        },threadPoolExecutor);

        CompletableFuture<Void> categoryViewCompletableFuture = skuInfoCompletableFuture.thenAcceptAsync(skuInfo -> {
            //通过三级分类id查询分类信息
            BaseCategoryView categoryView = productFeignClient.getCategoryView(skuInfo.getCategory3Id());
            //保存商品分类数据
            resultMap.put("categoryView", categoryView);
        },threadPoolExecutor);
        CompletableFuture<Void> valuesSkuJsonCompletableFuture = skuInfoCompletableFuture.thenAcceptAsync(skuInfo -> {
            //根据spuId 获取由销售属性值id和skuId所组成的Map集合数据{获取销售属性值切换数据}
            Map skuValueIdsMap = productFeignClient.getSkuValueIdsMap(skuInfo.getSpuId());
            //保存销售属性值切换的数据，但前台需要的时json，要将skuValueMap转化为json字符串
            String valuesSkuJson = JSON.toJSONString(skuValueIdsMap);
            resultMap.put("valuesSkuJson", valuesSkuJson);
        },threadPoolExecutor);

        CompletableFuture<Void> skuPriceCompletableFuture = CompletableFuture.runAsync(() -> {
            //根据skuId获取sku价格
            BigDecimal skuPrice = productFeignClient.getSkuPrice(skuId);
            //保存价格
            resultMap.put("price",skuPrice);
        },threadPoolExecutor);

        CompletableFuture<Void> incrHotScoreCompletableFuture = CompletableFuture.runAsync(() -> {
            //调用热度排名方法
            listFeignClient.incrHotScore(skuId);
        }, threadPoolExecutor);

        // 数据汇总
        CompletableFuture.allOf(skuInfoCompletableFuture,
                spuSaleAttrListCompletableFuture,
                categoryViewCompletableFuture,
                valuesSkuJsonCompletableFuture,
                skuPriceCompletableFuture,
                incrHotScoreCompletableFuture).join();
        //将所有数据封装返回到resultMap
        return resultMap;






        //第一步：获取resultMap中的所有value信息
        //根据skuId获取sku信息{包含skuImage}
//        SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);
        //通过三级分类id查询分类信息
//        BaseCategoryView categoryView = productFeignClient.getCategoryView(skuInfo.getCategory3Id());
        //根据skuId获取sku价格
//        BigDecimal skuPrice = productFeignClient.getSkuPrice(skuId);
        //根据spuId，skuId 查询销售属性集合
//        List<SpuSaleAttr> spuSaleAttrListCheckBySku = productFeignClient.getSpuSaleAttrListCheckBySku(skuId, skuInfo.getSpuId());
        //根据spuId 获取由销售属性值id和skuId所组成的Map集合数据{获取销售属性值切换数据}
//        Map skuValueIdsMap = productFeignClient.getSkuValueIdsMap(skuInfo.getSpuId());

        //第二步：保存信息 将key和value放入resultMap中需要根据前端页面结合在一起使用
        // 上面我们得到了value信息，但key=""呢，{key是由商品详情页面决定的，
        // 需要从找到页面对应的key，然后将value信息 ${skuInfo}存入ResultMap中}
        // 这个商品详情页面后续会提供，先以课件为准

        //保存skuInfo信息{包含skuImage}
//        resultMap.put("skuInfo",skuInfo);
        //保存商品分类数据
//        resultMap.put("categoryView",categoryView);
        //保存价格
//        resultMap.put("price",skuPrice);
        //保存销售属性及属性值数据
//        resultMap.put("spuSaleAttrList",spuSaleAttrListCheckBySku);
        //保存销售属性值切换的数据，但前台需要的时json，要将skuValueMap转化为json字符串
//        String valuesSkuJson = JSON.toJSONString(skuValueIdsMap);
//        resultMap.put("valuesSkuJson",valuesSkuJson);

    }
}
