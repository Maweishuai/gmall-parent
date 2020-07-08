package com.atguigu.gmall.cart.service;

import com.atguigu.gmall.model.cart.CartInfo;

/**
 * @author Administrator
 * @create 2020-06-27 1:00
 */
public interface CartAsyncService {

    /**
     * 修改购物车
     * @param cartInfo
     */
    void updateCartInfo(CartInfo cartInfo);

    /**
     * 保存购物车
     * @param cartInfo
     */
    void saveCartInfo(CartInfo cartInfo);

    /**
     * 删除未登录的缓存
     * @param userId
     */
    void deleteCartInfo(String userId);

    /**
     * 更新选中状态
     * @param userId
     * @param isChecked
     * @param skuId
     */
    void checkCart(String userId, Integer isChecked, Long skuId);

    /**
     * 删除购物车
     * @param userId
     * @param skuId
     */
    void deleteCartInfo(String userId, Long skuId);
}
