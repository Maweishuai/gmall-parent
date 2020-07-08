package com.atguigu.gmall.order.controller;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.cart.client.CartFeignClient;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.util.AuthContextHolder;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.user.UserAddress;
import com.atguigu.gmall.order.service.OrderService;
import com.atguigu.gmall.product.client.ProductFeignClient;
import com.atguigu.gmall.user.client.UserFeignClient;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author Administrator
 * @create 2020-06-28 1:15
 */
@RestController
@RequestMapping("api/order")
public class OrderApiController {

    @Autowired
    private UserFeignClient userFeignClient;

    @Autowired
    private CartFeignClient cartFeignClient;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductFeignClient productFeignClient;

    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;


    /**
     * 确认订单
     * @param request
     * @return
     */
    @GetMapping("auth/trade")
    public Result<Map<String, Object>> trade(HttpServletRequest request){
        //1.获取用户的地址信息，从service-user模块
        //获取用户Id
        String userId = AuthContextHolder.getUserId(request);
        //获取用户地址列表 根据用户Id
        List<UserAddress> userAddressList = userFeignClient.findUserAddressListByUserId(userId);

        //2.获取购物车选中的送货清单，从service-cart模块
        List<CartInfo> cartCheckedList = cartFeignClient.getCartCheckedList(userId);
        // 声明一个集合来存储订单明细
        List<OrderDetail> detailArrayList = new ArrayList<>();
        // 循环遍历cartCheckedList，将数据赋值给orderDetail
        for (CartInfo cartInfo : cartCheckedList) {
            // 将cartInfo 赋值给 orderDetail
            OrderDetail orderDetail = new OrderDetail();
            orderDetail.setImgUrl(cartInfo.getImgUrl());
            orderDetail.setOrderPrice(cartInfo.getSkuPrice());
            orderDetail.setSkuName(cartInfo.getSkuName());
            orderDetail.setSkuNum(cartInfo.getSkuNum());
            orderDetail.setSkuId(cartInfo.getSkuId());

            // 将每一个orderDeatil 添加到集合orderDetailList中
            detailArrayList.add(orderDetail);
        }
        // 存储总金额
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setOrderDetailList(detailArrayList);
        // 计算总金额
        orderInfo.sumTotalAmount();

        // 声明一个map 集合来存储数据
        Map<String , Object> map = new HashMap<>();
        // 存储订单明细
        map.put("detailArrayList",detailArrayList);
        // 存储收货地址列表
        map.put("userAddressList",userAddressList);
        // 保存总金额
        map.put("totalNum", detailArrayList.size());
        map.put("totalAmount", orderInfo.getTotalAmount());

        return Result.ok(map);
    }

    /**
     * 提交订单
     * @param orderInfo
     * @param request
     * @return
     */
    @PostMapping("auth/submitOrder")
    public Result submitOrder(@RequestBody OrderInfo orderInfo,
                              HttpServletRequest request){
        //获取用户Id 补缺页面缺少的字段
        String userId = AuthContextHolder.getUserId(request);
        orderInfo.setUserId(Long.parseLong(userId));

        // 1获取页面提交过来的流水号
        String tradeNo = request.getParameter("tradeNo");
        // 2开始比较
        boolean flag = orderService.checkTradeCode(userId,tradeNo);
        // 如果返回的是true ，则说明第一次提交，如果是false 说明无刷新重复提交了！
        // 异常情况
        if (flag){
            // 如果是false 说明无刷新重复提交了！
            return Result.fail().message("不能重复提交订单!");
        }
        // 3删除流水号
        orderService.deleteTradeNo(userId);

        // 创建一个集合对象，来存储异常信息
        List<String> errorList = new ArrayList<>();
        // 使用异步编排来执行
        // 声明一个集合来存储异步编排对象
        List<CompletableFuture> futureList = new ArrayList<>();
        // 验证库存：验证每个商品，存在orderDetailList
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        if (!CollectionUtils.isEmpty(orderDetailList)){
            for (OrderDetail orderDetail : orderDetailList) {
                // 开一个异步编排
                CompletableFuture<Void> checkStockCompletableFuture = CompletableFuture.runAsync(() -> {
                    // 调用查询库存方法
                    boolean result = orderService.checkStock(orderDetail.getSkuId(), orderDetail.getSkuNum());
                    if (!result) {
                        // 提示信息某某商品库存不足
                        // return Result.fail().message(orderDetail.getSkuName()+"库存不足！");
                        errorList.add(orderDetail.getSkuName() + "库存不足！");
                    }
                }, threadPoolExecutor);
                // 将验证库存的异步编排对象放入这个集合
                futureList.add(checkStockCompletableFuture);

                // 利用另一个异步编排来验证价格
                CompletableFuture<Void> skuPriceCompletableFuture = CompletableFuture.runAsync(() -> {
                    // 获取到商品的实时价格
                    BigDecimal skuPrice = productFeignClient.getSkuPrice(orderDetail.getSkuId());
                    // 判断 价格有变化，要么大于 1 ，要么小于 -1。说白了 ,相等 0
                    if (orderDetail.getOrderPrice().compareTo(skuPrice) != 0) {
                        // 如果价格有变动，则重新查询。
                        // 订单的价格来自于购物车，只需要将购物车的价格更改了，重新下单就可以了。
                        cartFeignClient.loadCartCache(userId);
                        //return Result.fail().message(orderDetail.getSkuName()+"价格有变动,请重新下单！");
                        errorList.add(orderDetail.getSkuName()+"价格有变动,请重新下单！");
                    }
                }, threadPoolExecutor);
                // 将验证价格的异步编排添加到集合中
                futureList.add(skuPriceCompletableFuture);
            }
        }
        // 合并线程 所有的异步编排都在futureList
        CompletableFuture.allOf(futureList.toArray(new CompletableFuture[futureList.size()])).join();
        // 返回页面提示信息
        if (errorList.size()>0){
            // 获取异常集合的数据
            return Result.fail().message(StringUtils.join(errorList,","));
        }
//        // 验证库存：
//        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
//        if (!CollectionUtils.isEmpty(orderDetailList)){
//            for (OrderDetail orderDetail : orderDetailList) {
//                // 调用查询库存方法
//                boolean result = orderService.checkStock(orderDetail.getSkuId(), orderDetail.getSkuNum());
//                if (!result){
//                    // 提示信息某某商品库存不足
//                    return Result.fail().message(orderDetail.getSkuName()+"库存不足！");
//                }
//
//                // 验证价格：
//                // 获取到商品的实时价格
//                BigDecimal skuPrice = productFeignClient.getSkuPrice(orderDetail.getSkuId());
//                // 判断 价格有变化，要么大于 1 ，要么小于 -1。说白了 ,相等 0
//                if (orderDetail.getOrderPrice().compareTo(skuPrice)!=0){
//                    // 如果价格有变动，则重新查询。
//                    // 订单的价格来自于购物车，只需要将购物车的价格更改了，重新下单就可以了。
//                    cartFeignClient.loadCartCache(userId);
//                    return Result.fail().message(orderDetail.getSkuName()+"价格有变动,请重新下单！");
//                }
//            }
//        }
        // 验证通过，保存订单！
        Long orderId = orderService.saveOrderInfo(orderInfo);
        //返回订单Id
        return Result.ok(orderId);
    }

    /**
     * 内部调用获取订单
     * @param orderId
     * @return
     */
    @GetMapping("inner/getOrderInfo/{orderId}")
    public OrderInfo getOrderInfo(@PathVariable Long orderId){
        return orderService.getOrderInfo(orderId);
    }

    /**
     * 拆单业务
     * @param request
     * @return
     */
    @RequestMapping("orderSplit")
    public String orderSplit(HttpServletRequest request){
        // 获取原始订单Id
        String orderId = request.getParameter("orderId");
        // 获取需要拆单的集合--仓库编号与商品的对照关系
        // [{"wareId":"1","skuIds":["2","10"]},{"wareId":"2","skuIds":["3"]}]
        String wareSkuMap = request.getParameter("wareSkuMap");

        // 开始拆单：获取到的子订单集合
        // 通过原始订单Id和 wareSkuMap获取到子订单集合
        List<OrderInfo> subOrderInfoList = orderService.orderSplit(Long.parseLong(orderId),wareSkuMap);
        // 声明一个存储map的集合
        List<Map> mapList = new ArrayList<>();
        // 将子订单中的部分数据转换为json 字符串--生成子订单集合
        for (OrderInfo orderInfo : subOrderInfoList) {
            // 运用initWareOrder方法 将部分数据转换为map
            Map map = orderService.initWareOrder(orderInfo);
            // 添加到集合中
            mapList.add(map);
        }
        // 返回子订单的json 字符串
        return JSON.toJSONString(mapList);
    }

    /**
     * 秒杀提交订单，秒杀订单不需要做前置判断，直接下单
     * @param orderInfo
     * @return
     */
    @PostMapping("inner/seckill/submitOrder")
    public Long submitOrder(@RequestBody OrderInfo orderInfo){
        // 调用保存订单方法
        Long orderId = orderService.saveOrderInfo(orderInfo);
        // 返回订单Id
        return orderId;
    }
}
