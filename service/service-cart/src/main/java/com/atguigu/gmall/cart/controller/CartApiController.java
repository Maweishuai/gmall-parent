package com.atguigu.gmall.cart.controller;

import com.atguigu.gmall.cart.service.CartService;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.util.AuthContextHolder;
import com.atguigu.gmall.model.cart.CartInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @author Administrator
 * @create 2020-06-27 1:15
 */
@RestController
@RequestMapping("api/cart")
public class CartApiController {

    @Autowired
    private CartService cartService;

    /**
     * 添加购物车
     * @param skuId
     * @param skuNum
     * @param request
     * @return
     */
    @PostMapping("addToCart/{skuId}/{skuNum}")
    public Result addToCart(@PathVariable Long skuId,
                            @PathVariable Integer skuNum,
                            HttpServletRequest request){
        // 获取userId
        // 需要一个用户Id ，是从网关传递过来的！
        // 用户信息都放入了header 中！common-util 中有工具类AuthContextHolder
        // 获取登录的用户Id，添加购物车的时候，一定会有登录的用户Id么？不一定！
            String userId = AuthContextHolder.getUserId(request);
        if (StringUtils.isEmpty(userId)){
            //如果userId为空 属于未登录时 获取临时用户Id
            userId = AuthContextHolder.getUserTempId(request);
        }
        //调用添加购物车方法
        cartService.addToCart(skuId,userId,skuNum);

        return Result.ok();
    }

    //----------------以下控制器方法不需要通过web-all来访问，属于ajax异步请求------------
    /**
     * 查询购物车
     * @param request
     * @return
     */
    @GetMapping("cartList")
    public Result cartList(HttpServletRequest request){
        // 获取userId
        String userId = AuthContextHolder.getUserId(request);
        // 获取userTempId
        String userTempId = AuthContextHolder.getUserTempId(request);

        //调用查询购物车方法
        List<CartInfo> cartInfoList  = cartService.getCartList(userId, userTempId);

        return Result.ok(cartInfoList );
    }

    /**
     * 更新选中状态
     *
     * @param skuId
     * @param isChecked
     * @param request
     * @return
     */
    @GetMapping("checkCart/{skuId}/{isChecked}")
    public Result checkCart(@PathVariable Long skuId,
                            @PathVariable Integer isChecked,
                            HttpServletRequest request){
        //获取用户Id
        String userId = AuthContextHolder.getUserId(request);
        // update cartInfo set isChecked=? where  skuId = ? and userId=?
        if (StringUtils.isEmpty(userId)){
            //为空 说明未登录 获取用户临时Id
            userId = AuthContextHolder.getUserTempId(request);
        }
        //调用更新方法
        cartService.checkCart(userId,isChecked,skuId);

        return Result.ok();
    }

    /**
     * 删除购物车
     * @param skuId
     * @param request
     * @return
     */
    @DeleteMapping("deleteCart/{skuId}")
    public Result deleteCart(@PathVariable Long skuId,HttpServletRequest request){
        //获取userId
        String userId = AuthContextHolder.getUserId(request);
        if (StringUtils.isEmpty(userId)) {
            // 获取临时用户Id
            userId = AuthContextHolder.getUserTempId(request);
        }
        cartService.deleteCart(skuId,userId);
        return Result.ok();
    }

    /**
     * 根据用户Id 查询送货清单数据
     *
     * @param userId
     * @return
     */
    @GetMapping("getCartCheckedList/{userId}")
    public List<CartInfo> getCartCheckedList(@PathVariable String userId) {
        return cartService.getCartCheckedList(userId);
    }


    /**
     *  根据用户Id 查询数据库并将数据放入缓存
     * @param userId
     * @return
     */
    @GetMapping("loadCartCache/{userId}")
    public Result loadCartCache(@PathVariable("userId") String userId) {
        cartService.loadCartCache(userId);
        return Result.ok();
    }
}
