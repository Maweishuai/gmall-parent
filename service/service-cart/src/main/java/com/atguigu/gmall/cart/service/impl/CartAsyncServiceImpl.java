package com.atguigu.gmall.cart.service.impl;

import com.atguigu.gmall.cart.mapper.CartInfoMapper;
import com.atguigu.gmall.cart.service.CartAsyncService;
import com.atguigu.gmall.model.cart.CartInfo;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * @author Administrator
 * @create 2020-06-27 1:02
 */
@Service
@Slf4j  //slf4j是一个日志标准
public class CartAsyncServiceImpl implements CartAsyncService {

    @Autowired
    private CartInfoMapper cartInfoMapper;

    @Async
    @Override
    public void updateCartInfo(CartInfo cartInfo) {
        cartInfoMapper.updateById(cartInfo);
    }

    @Async
    @Override
    public void saveCartInfo(CartInfo cartInfo) {
        cartInfoMapper.insert(cartInfo);
    }

    @Async
    @Override
    public void deleteCartInfo(String userId) {
        QueryWrapper<CartInfo> cartInfoQueryWrapper = new QueryWrapper<>();
        cartInfoQueryWrapper.eq("user_id",userId);
        cartInfoMapper.delete(cartInfoQueryWrapper);
    }

    @Async
    @Override
    public void checkCart(String userId, Integer isChecked, Long skuId) {
        // update cartInfo set is_checked = isChecked where user_id =userId and sku_id = skuId;
        // 第一个参数，表示要修改的数据
        CartInfo cartInfo = new CartInfo();
        cartInfo.setIsChecked(isChecked);
        // 第二个参数, 表示更新条件
        QueryWrapper<CartInfo> cartInfoQueryWrapper = new QueryWrapper<>();
        cartInfoQueryWrapper.eq("user_id", userId);
        cartInfoQueryWrapper.eq("sku_id", skuId);
        // 第一个参数，表示要修改的数据 第二个参数, 表示更新条件
        cartInfoMapper.update(cartInfo,cartInfoQueryWrapper);
    }

    @Async
    @Override
    public void deleteCartInfo(String userId, Long skuId) {
        QueryWrapper<CartInfo> cartInfoQueryWrapper = new QueryWrapper<>();
        cartInfoQueryWrapper.eq("user_id",userId).eq("sku_id",skuId);
        cartInfoMapper.delete(cartInfoQueryWrapper);
    }
}
