package com.atguigu.gmall.product.controller;

import com.atguigu.gmall.model.product.BaseTrademark;
import com.atguigu.gmall.product.service.BaseTrademarkService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.atguigu.gmall.common.result.Result;

import java.util.List;

/**
 * @author Administrator
 * @create 2020-06-11 23:26
 */
@RestController
@RequestMapping("/admin/product/baseTrademark")
public class BaseTrademarkController {

    @Autowired
    private BaseTrademarkService baseTrademarkService;

    /**
     * 获取分页列表
     * @param
     * @return
     * http://api.gmall.com/admin/product/baseTrademark/{page}/{limit}
     */
    @GetMapping("{page}/{limit}")
    public Result getPageList(@PathVariable Long page,
                              @PathVariable Long limit){
        Page<BaseTrademark> pageParam = new Page<>(page,limit);
        IPage<BaseTrademark> baseTrademarkIPage = baseTrademarkService.selectPage(pageParam);

        return Result.ok(baseTrademarkIPage);
    }

    /**
     * 添加品牌
     *  http://api.gmall.com/admin/product/baseTrademark/save
     */
    @PostMapping("save")
    public Result save(@RequestBody BaseTrademark baseTrademark){

        baseTrademarkService.save(baseTrademark);
        return Result.ok();
    }
    /**
     * 修改品牌
     *  http://api.gmall.com/admin/product/baseTrademark/update
     */
    @PutMapping("update")
    public Result upate(@RequestBody BaseTrademark baseTrademark){

        baseTrademarkService.updateById(baseTrademark);
        return Result.ok();
    }

    /**
     * 删除品牌
     *  http://api.gmall.com/admin/product/baseTrademark/remove/{id}
     */
    @DeleteMapping("remove/{id}")
    public Result remove(@PathVariable Long id){

        baseTrademarkService.removeById(id);
        return Result.ok();
    }
    /*
    *根据id获取BaseBaseTrademark品牌
    *http://api.gmall.com/admin/product/baseTrademark/get/{id}
    */
    @GetMapping("get/{id}")
    public Result get(@PathVariable Long id){

        BaseTrademark baseTrademark = baseTrademarkService.getById(id);
        return Result.ok(baseTrademark);
    }

    /**
     * 查询全部品牌
     * @return
     * http://api.gmall.com/admin/product/baseTrademark/getTrademarkList
     */
    @GetMapping("getTrademarkList")
    public Result<List<BaseTrademark>> getTrademarkList() {
        List<BaseTrademark> baseTrademarkList = baseTrademarkService.getTrademarkList();
        return Result.ok(baseTrademarkList);
    }
}
