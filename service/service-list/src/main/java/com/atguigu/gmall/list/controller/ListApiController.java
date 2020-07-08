package com.atguigu.gmall.list.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.list.service.SearchService;
import com.atguigu.gmall.model.list.Goods;
import com.atguigu.gmall.model.list.SearchParam;
import com.atguigu.gmall.model.list.SearchResponseVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/**
 * <p>
 * 商品搜索列表接口
 * </p>
 *
 */
@RestController
@RequestMapping("api/list")
public class ListApiController {

    //利用注解在es中创建mapping
    @Autowired
    private ElasticsearchRestTemplate esRestTemplate;

    @Autowired
    private SearchService searchService;

    //localhost:8203/inner/createIndex
    @GetMapping("inner/createIndex")
    public Result createIndex() {
        //创建index和type
        esRestTemplate.createIndex(Goods.class);
        esRestTemplate.putMapping(Goods.class);

        return Result.ok();
    }

    /**
     * 上架商品
     * @param skuId
     * @return
     */
    @GetMapping("inner/upperGoods/{skuId}")
    public Result upperGoods(@PathVariable Long skuId) {
        searchService.upperGoods(skuId);
        return Result.ok();
    }

    /**
     * 下架商品
     * @param skuId
     * @return
     */
    @GetMapping("inner/lowerGoods/{skuId}")
    public Result lowerGoods(@PathVariable Long skuId) {
        searchService.lowerGoods(skuId);
        return Result.ok();
    }

    /**
     * 更新商品incrHotScore
     *
     * @param skuId
     * @return
     */
    @GetMapping("inner/incrHotScore/{skuId}")
    public Result incrHotScore(@PathVariable Long skuId){

        searchService.incrHotScore(skuId);
        return Result.ok();
    }

    /**
     * 搜索商品
     * @param searchParam
     * @return
     * @throws IOException
     */
    @PostMapping
    public Result getList(@RequestBody SearchParam searchParam){
        SearchResponseVo search = null;
        try {
            search = searchService.search(searchParam);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Result.ok(search);
    }
}
