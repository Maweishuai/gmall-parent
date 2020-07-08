package com.atguigu.gmall.product.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.product.*;
import com.atguigu.gmall.product.service.ManageService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author Administrator
 * @create 2020-06-10 0:48
 */
@Api(tags ="商品基础属性接口")
@RestController
@RequestMapping("admin/product")
public class BaseManageController {

    @Autowired
    private ManageService manageService;
    //当我们向vue传送数据时，需要将java对象转化成json数据，
    //@RestController注解=@ResponseBody+@Controller,已经将其转化完了
    //使用common中封装好的Result类 作为统一的结果集类，全局统一返回结果

    /**
     * 查询所有的一级分类信息
     * @return
     * http://api.gmall.com/admin/product/getCategory1
     */
    @GetMapping("getCategory1")
    public Result getCategory1(){
        List<BaseCategory1> category1List = manageService.getCategory1();
        return Result.ok(category1List);
    }

    /**
     * 根据一级分类Id 查询二级分类数据
     * @param category1Id
     * @return
     * http://api.gmall.com/admin/product/getCategory2/{category1Id}
     */
    @GetMapping("getCategory2/{category1Id}")
    public Result getCategory2(@PathVariable Long category1Id){
        List<BaseCategory2> category2List = manageService.getCategory2(category1Id);
        return Result.ok(category2List);
    }

    /**
     * 根据二级分类Id 查询三级分类数据
     * @param category2Id
     * @return
     * http://api.gmall.com/admin/product/getCategory3/{category2Id}
     */
    @GetMapping("getCategory3/{category2Id}")
    public Result getCategory3(@PathVariable Long category2Id){
        List<BaseCategory3> category3List = manageService.getCategory3(category2Id);
        return Result.ok(category3List);
    }

    /**
     * 根据分类Id 获取平台属性数据
     * @param category1Id
     * @param category2Id
     * @param category3Id
     * @return
     * http://api.gmall.com/admin/product/attrInfoList/{category1Id}/{category2Id}/{category3Id}
     */
    @GetMapping("attrInfoList/{category1Id}/{category2Id}/{category3Id}")
    public Result attrInfoList(@PathVariable Long category1Id,
                               @PathVariable Long category2Id,
                               @PathVariable Long category3Id){
        List<BaseAttrInfo> attrInfoList = manageService.getArrInfoList(category1Id, category2Id, category3Id);
        return Result.ok(attrInfoList);
    }

    /**
     * 保存平台属性方法
     * @param baseAttrInfo
     * @return
     * http://api.gmall.com/admin/product/saveAttrInfo
     *     为什么用BaseAttrInfo去接收传过来的数据？
     *    因为这个实体类中既有平台属性的数据，也有平台属性值的数据！
     *    vue 项目在页面传递过来的是json 字符串， 能否直接映射成java 对象？不能！
     *    必须用注解@RequestBody： 将Json 数据转换为Java 对象。
     *  前台数据都被封装到该对象中baseAttrInfo
     */
    @PostMapping("saveAttrInfo")
    public Result saveAttrInfo(@RequestBody BaseAttrInfo baseAttrInfo){
        manageService.saveAttrInfo(baseAttrInfo);
        return Result.ok();
    }

    /**
     * 根据平台属性ID获取平台属性
     * @param attrId：平台属性ID
     * @return
     * http://api.gmall.com/admin/product/getAttrValueList/{attrId}
     */
    @GetMapping("getAttrValueList/{attrId}")
    public Result<List<BaseAttrValue>> getAttrValueList(@PathVariable Long attrId){
        //步骤：先查平台属性，再从平台属性中获取平台属性值
        //1.获取平台属性对象
        BaseAttrInfo baseAttrInfo = manageService.getAttrInfo(attrId);
        //2.因为平台属性值集合在平台属性中，那就利用平台属性得到平台属性值集合
        List<BaseAttrValue> attrValueList = baseAttrInfo.getAttrValueList();
        //3.将平台属性数据返回给前端页面
        return Result.ok(attrValueList);
    }

    /**
     * spu分页查询
     *      * @param pageParam
     *      * @param spuInfo
     *      * @return
     *      * 分页查询 多个spuInfo 必须指定，查询第几页，
     *      * 每页显示的数据条数，是否有抽出条件 {category3Id=?}。
     *      * param spuInfo 因为spuInfo 实体类的属性中有一个属性叫category3Id | spring mvc 封装对象传值
     *      * http://api.gmall.com/admin/product/{page}/{limit}?category3Id=61
     */
    @GetMapping("{page}/{limit}")
    public Result<IPage<SpuInfo>> getPageList(@PathVariable Long page,
                                              @PathVariable Long limit,
                                              SpuInfo spuInfo){
        Page<SpuInfo> pageParam = new Page<>(page, limit);

        IPage<SpuInfo> spuInfoIPage = manageService.selectPage(pageParam, spuInfo);

        return Result.ok(spuInfoIPage);
    }

    /**
     * SKU分页列表
     * @param page
     * @param limit
     * @return
     */
    @GetMapping("/list/{page}/{limit}")
    public Result getList(@PathVariable Long page,
                          @PathVariable Long limit){

        Page<SkuInfo> pageParam = new Page<>(page,limit);
        IPage<SkuInfo> skuInfoIPage = manageService.selectPage(pageParam);
        return Result.ok(skuInfoIPage);
    }

    /**
     * 商品上架
     * @param skuId
     * @return
     */
    @GetMapping("onSale/{skuId}")
    public Result onSale(@PathVariable Long skuId) {
        manageService.onSale(skuId);
        return Result.ok();
    }

    /**
     * 商品下架
     * @param skuId
     * @return
     */
    @GetMapping("cancelSale/{skuId}")
    public Result cancelSale(@PathVariable Long skuId) {
        manageService.cancelSale(skuId);
        return Result.ok();
    }

}
