package com.atguigu.gmall.product.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.model.product.SpuImage;
import com.atguigu.gmall.model.product.SpuSaleAttr;
import com.atguigu.gmall.product.service.ManageService;
import com.baomidou.mybatisplus.extension.api.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author Administrator
 * @create 2020-06-13 0:11
 */
@RestController
@RequestMapping("admin/product")
public class SkuManageController {

    @Autowired
    private ManageService manageService;

    /**
     * 根据spuId 查询spuImageList
     * @param spuId
     * @return
     */
    @GetMapping("spuImageList/{spuId}")
    public Result getSpuImageList(@PathVariable Long spuId){
        List<SpuImage> spuImageList = manageService.getSpuImageList(spuId);
        // 返回图片列表
        return Result.ok(spuImageList);
    }
    /**
     * 根据spuId 查询销售属性集合
     * @param spuId
     * @return
     */
    //http://api.gmall.com/admin/product/spuSaleAttrList/16
    @GetMapping("spuSaleAttrList/{spuId}")
    public Result getSpuSaleAttrList(@PathVariable Long spuId){
        List<SpuSaleAttr> spuSaleAttrList = manageService.spuSaleAttrList(spuId);

        return Result.ok(spuSaleAttrList);
    }
    /**
     * 大保存sku
     * sku_info，sku_attr_value，sku_sale_attr_value，sku_image
     * @param skuInfo
     * @return
     */
    @PostMapping("saveSkuInfo")
    public Result saveSkuInfo(@RequestBody SkuInfo skuInfo){

        manageService.saveSkuInfo(skuInfo);
        return Result.ok();
    }}
