package com.atguigu.gmall.product.service;

import com.atguigu.gmall.model.product.BaseTrademark;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author mqx
 * @date 2020/6/10 14:41
 */

public interface BaseTrademarkService extends IService<BaseTrademark> {

    // 分页查询品牌数据
    IPage<BaseTrademark> selectPage(Page<BaseTrademark> pageParam);

    //查询所有品牌数据
    List<BaseTrademark> getTrademarkList();
}
