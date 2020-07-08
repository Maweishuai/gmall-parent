package com.atguigu.gmall.item.client;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.item.client.impl.ItemDegradeFeignClient;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * @author Administrator
 * @create 2020-06-15 12:54
 */
@FeignClient(value = "service-item", fallback = ItemDegradeFeignClient.class)
public interface ItemFeignClient {

    // ItemFeignClient这个远程调用接口
    // {调用的是微服务service-item中的内部接口ItemApiController}
    // 而内部接口ItemApiController控制器中
    // 封装的数据是为了提供给微服务web-all使用的。

    /**
     * 根据skuId 获取商品详情的Map信息Result
     * @param skuId
     * @return
     */
    @GetMapping("api/item/{skuId}")
    Result getItem(@PathVariable Long skuId);

}
