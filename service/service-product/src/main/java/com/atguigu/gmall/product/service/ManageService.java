package com.atguigu.gmall.product.service;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.model.product.*;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * @author Administrator
 * @create 2020-06-09 22:45
 */

public interface ManageService {

    //查询所有的一级分类数据
    List<BaseCategory1> getCategory1();

    //根据一级分类id 查询二级分类数据
    List<BaseCategory2> getCategory2(Long category1Id);

    //根据二级分类id 查询三级分类数据
    List<BaseCategory3> getCategory3(Long category2Id);

    //根据三个分类Id 查询平台属性数据
    /*
     * 接口说明：
     *      1，平台属性可以挂在一级分类、二级分类和三级分类
     *      2，查询一级分类下面的平台属性，传：category1Id，0，0；   取出该分类的平台属性
     *      3，查询二级分类下面的平台属性，传：category1Id，category2Id，0；
     *         取出对应一级分类下面的平台属性与二级分类对应的平台属性
     *      4，查询三级分类下面的平台属性，传：category1Id，category2Id，category3Id；
     *         取出对应一级分类、二级分类与三级分类对应的平台属性
     * */
    List<BaseAttrInfo> getArrInfoList(Long category1Id, Long category2Id, Long category3Id);

    /**
     * 保存平台属性和平台属性值。
     * 将平台属性和平台属性值都放到baseAttrInfo中 保存到数据库
     *
     * @param baseAttrInfo
     */
    void saveAttrInfo(BaseAttrInfo baseAttrInfo);

    /**
     * 根据平台属性ID获取平台属性
     *
     * @param attrId：平台属性ID
     */
    BaseAttrInfo getAttrInfo(Long attrId);

    /**
     * spu分页查询
     *
     * @param pageParam
     * @param spuInfo
     * @return 分页查询 多个spuInfo 必须指定，查询第几页，
     * 每页显示的数据条数，是否有抽出条件 {category3Id=?}。
     * param spuInfo 因为spuInfo 实体类的属性中有一个属性叫category3Id | spring mvc 封装对象传值
     * http://api.gmall.com/admin/product/{page}/{limit}?category3Id=61
     */
    IPage<SpuInfo> selectPage(Page<SpuInfo> pageParam, SpuInfo spuInfo);

    /**
     * 查询所有的销售属性数据
     *
     * @return
     */
    List<BaseSaleAttr> getBaseSaleAttrList();

    /**
     * 保存spu
     *
     * @param spuInfo
     * @return
     */
    void saveSpuInfo(SpuInfo spuInfo);

    /**
     * 根据spuId 查询spuImageList
     *
     * @param spuId
     * @return
     */
    List<SpuImage> getSpuImageList(Long spuId);

    /**
     * 根据spuId 查询销售属性集合
     *
     * @param spuId
     * @return
     */
    List<SpuSaleAttr> spuSaleAttrList(Long spuId);

    /**
     * 大保存sku
     * sku_info，sku_attr_value，sku_sale_attr_value，sku_image
     *
     * @param skuInfo
     * @return
     */
    void saveSkuInfo(SkuInfo skuInfo);

    /**
     * SKU分页列表
     *
     * @param pageParam
     * @return
     */
    IPage<SkuInfo> selectPage(Page<SkuInfo> pageParam);

    /**
     * 商品上架
     *
     * @param skuId
     */
    void onSale(Long skuId);

    /**
     * 商品下架
     *
     * @param skuId
     */
    void cancelSale(Long skuId);

    /**
     * 根据skuId 查询skuInfo
     *
     * @param skuId
     * @return
     */
    SkuInfo getSkuInfo(Long skuId);

    /**
     * 通过三级分类id查询分类信息
     *
     * @param category3Id
     * @return
     */
    BaseCategoryView getCategoryView(Long category3Id);

    /**
     * 根据skuId获取sku价格
     *
     * @param skuId
     * @return
     */
    BigDecimal getSkuPrice(Long skuId);

    /**
     * 根据spuId，skuId 查询销售属性集合
     *
     * @param skuId
     * @param spuId
     * @return
     */
    List<SpuSaleAttr> getSpuSaleAttrListCheckBySku(Long skuId, Long spuId);

    /**
     * 根据spuId 查询map 集合属性
     *
     * @param spuId
     * @return
     */
    Map getSkuValueIdsMap(Long spuId);

    /**
     * 获取全部分类信息
     *
     * @return
     */
    List<JSONObject> getBaseCategoryList();

    /**
     * 根据品牌Id获取品牌数据
     * @param tmId
     * @return
     */
    BaseTrademark getTrademarkByTmId(Long tmId);

    /**
     * 通过skuId 集合来查询数据
     * @param skuId
     * @return
     */
    List<BaseAttrInfo> getAttrList(Long skuId);
}
