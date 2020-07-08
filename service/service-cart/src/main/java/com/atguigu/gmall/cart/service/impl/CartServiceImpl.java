package com.atguigu.gmall.cart.service.impl;

import com.atguigu.gmall.cart.mapper.CartInfoMapper;
import com.atguigu.gmall.cart.service.CartAsyncService;
import com.atguigu.gmall.cart.service.CartService;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.product.client.ProductFeignClient;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author Administrator
 * @create 2020-06-26 21:37
 */
@Service
public class CartServiceImpl implements CartService {

    @Autowired
    private CartInfoMapper cartInfoMapper;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private ProductFeignClient productFeignClient;

    //同步操作reids，异步更新mysql
    @Autowired
    private CartAsyncService cartAsyncService;


    /**
     * 添加购物车
     *
     * @param skuId
     * @param userId
     * @param skuNum
     */
    @Override
    public void addToCart(Long skuId, String userId, Integer skuNum) {
        // 数据要想放入缓存，必须使用redis,同样应该有key
        //获取购物车key
        // redis 中使用什么数据类型来存储购物车？
        // hash 使用 hset(key,field,value)  key=user:userId:cart field=skuId value=商品数据[cartInfoExist]
        String cartKey = getCartKey(userId);

        // 判断缓存中是否有cartKey，
        if (!redisTemplate.hasKey(cartKey)) {
            //如果没有  先加载数据库中的数据放入缓存！
            loadCartCache(userId);
        }
        // 添加购物车前先查看购物车中的数据
        // 根据userId和skuId
        // 获取购物车中数据库对象
        QueryWrapper<CartInfo> cartInfoQueryWrapper = new QueryWrapper<>();
        cartInfoQueryWrapper.eq("sku_id", skuId).eq("user_id", userId);
        CartInfo cartInfoExist = cartInfoMapper.selectOne(cartInfoQueryWrapper);
        //先判断购物车中的是否有以上数据
        if (null != cartInfoExist) {
            //如果不为空 说明有商品 可以直接加数量
            cartInfoExist.setSkuNum(cartInfoExist.getSkuNum() + skuNum);
            //获取实时价格
            //1.获取数据库原有价格
            BigDecimal skuPrice = productFeignClient.getSkuPrice(skuId);
            //2.获取实时价格
            cartInfoExist.setSkuPrice(skuPrice);

            //更新数据
//            cartInfoMapper.updateById(cartInfoExist);
            cartAsyncService.updateCartInfo(cartInfoExist);
            //存入数据库
//            redisTemplate.boundHashOps(cartKey).put(skuId.toString(),cartInfoExist);
        } else {
            //如果为空 说明没有商品 第一次添加
            //创建一个skuInfo对象 用来从商品详情页得到数据
            //购物车中的数据，都是来自于商品详情，商品详情的数据是来自于servce-product.
            SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);
            //创建一个cartInfo 用来封装添加到购物车的数据
            CartInfo cartInfo = new CartInfo();
            cartInfo.setSkuPrice(skuInfo.getPrice());
            cartInfo.setCartPrice(skuInfo.getPrice());
            cartInfo.setSkuNum(skuNum);
            cartInfo.setSkuName(skuInfo.getSkuName());
            cartInfo.setImgUrl(skuInfo.getSkuDefaultImg());
            cartInfo.setSkuId(skuId);
            cartInfo.setUserId(userId);

            // 添加数据库
//            cartInfoMapper.insert(cartInfo);
            cartAsyncService.saveCartInfo(cartInfo);
            //存入数据库
//            redisTemplate.boundHashOps(cartKey).put(skuId.toString(),cartInfo);
            // 如果代码走到了这，说明cartInfoExist 是空。cartInfoExist 可能会被GC吃了。
            // 废物再利用，将cartInfo的数据赋值给cartInfoExist，这样可以将存入缓存提出去
            cartInfoExist = cartInfo;
        }

        //将购物车数据放入缓存
//        redisTemplate.opsForHash().put(key,field,value);
//        redisTemplate.opsForHash().putAll(key,map); map.put(field,value);
//        redisTemplate.boundHashOps(key).put(field,value);
        redisTemplate.boundHashOps(cartKey).put(skuId.toString(), cartInfoExist);

        //设置过期时间
        setCartKeyExpire(cartKey);
    }


    /**
     * 展示购物车列表
     * 通过用户Id 查询购物车列表
     *
     * @param userId
     * @param userTempId
     * @return
     */
    @Override
    public List<CartInfo> getCartList(String userId, String userTempId) {
        // 声明一个返回的集合对象
        List<CartInfo> cartInfoList = new ArrayList<>();

        // 1.未登录：临时用户Id 获取未登录的购物车数据
        if (StringUtils.isEmpty(userId)) {
            cartInfoList = this.getCartList(userTempId);
            return cartInfoList;
        }
        // 2.已登录:用户Id 获取已登录的购物车数据
        /*
         1. 准备合并购物车
         2. 获取未登录的购物车数据
         3. 如果未登录购物车中有数据，则进行合并
            合并的条件：skuId 相同 则数量相加，合并完成之后，删除未登录的数据！
         4. 如果未登录购物车没有数据，则直接显示已登录的数据
          */
        if (!StringUtils.isEmpty(userId)) {
            //创建一个未登录的购物车数据集合
            List<CartInfo> cartInfoNoLoginList = getCartList(userTempId);
            //判断未登录时，购物车中是否有数据
            if (!CollectionUtils.isEmpty(cartInfoNoLoginList)) {
                //如果有 就进行合并
                //合并的条件：skuId 相同 则数量相加，合并完成之后，删除未登录的数据！
                cartInfoList = mergeToCartList(cartInfoNoLoginList, userId);
                // 合并之后 删除未登录购物车数据
                deleteCartList(userTempId);
            }
            // 如果未登录购物车中没有数据
            if (StringUtils.isEmpty(userTempId) || CollectionUtils.isEmpty(cartInfoNoLoginList)) {
                //那么就直接返回登录的购物车集合
                cartInfoList = getCartList(userId);
            }
        }
        return cartInfoList;
    }

    /**
     * 更新选中状态
     * @param userId
     * @param isChecked
     * @param skuId
     */
    @Override
    public void checkCart(String userId, Integer isChecked, Long skuId) {
        //1.更新数据库
        //调用异步对象更新数据
        cartAsyncService.checkCart(userId,isChecked,skuId);

        //2.更新缓存
        // 定义key user:userId:cart
        String cartKey = getCartKey(userId);
        //获取缓存中所有的购物车中的商品数据的key
        BoundHashOperations boundHashOperations = redisTemplate.boundHashOps(cartKey);
        // 获取在缓存中用户选择的商品
        if (boundHashOperations.hasKey(skuId.toString())){
            // 根据skuId 获取到对应的cartInfo
            CartInfo cartInfo = (CartInfo) boundHashOperations.get(skuId.toString());
            // 对应修改选中状态,放入缓存
            cartInfo.setIsChecked(isChecked);

            // 更新缓存
            // 修改完成之后，将修改好的cartInfo 放入缓存
            boundHashOperations.put(skuId.toString(),cartInfo);
            // 设置过期时间
            setCartKeyExpire(cartKey);
        }
    }

    /**
     * 删除购物车
     * @param skuId
     * @param userId
     */
    @Override
    public void deleteCart(Long skuId, String userId) {
        // 1.数据库删除{异步删除}
        cartAsyncService.deleteCartInfo(userId,skuId);

        // 2.缓存删除
        String cartKey = getCartKey(userId);
        //获取缓存对象
        BoundHashOperations boundHashOperations = redisTemplate.boundHashOps(cartKey);
        // 获取在缓存中用户选择的商品
        // 判断商品skuId 在缓存是否存在
        if (boundHashOperations.hasKey(skuId.toString())){
            // 如果存在，则删除
            boundHashOperations.delete(skuId.toString());
        }

    }

    /**
     * 根据用户Id 查询送货清单数据
     * @param userId
     * @return
     */
    @Override
    public List<CartInfo> getCartCheckedList(String userId) {
        List<CartInfo> cartInfoList = new ArrayList<>();
        // 查询购物车列表是因为，我们的送货清单是从购物车来的，购物车中选中的商品才是送货清单！
        // 在此直接查询缓存即可！
        // 定义key user:userId:cart
        String cartKey = this.getCartKey(userId);
        // 表示根据cartKey 能够获取到key 所对应的value
        List<CartInfo> cartCachInfoList = redisTemplate.opsForHash().values(cartKey);
        if (!CollectionUtils.isEmpty(cartCachInfoList)) {
            for (CartInfo cartInfo : cartCachInfoList) {
                // 获取选中的商品！
                if (cartInfo.getIsChecked().intValue() == 1) {
                    cartInfoList.add(cartInfo);
                }
            }
        }
        return cartInfoList;
    }

    /**
     * 提出方法--合并购物车
     *
     * @param cartInfoNoLoginList 未登录购物车数据
     * @param userId              登录的用户Id
     * @return
     */
    private List<CartInfo> mergeToCartList(List<CartInfo> cartInfoNoLoginList, String userId) {
        /*
        demo1:
            登录：
                37 1
                38 1
            未登录：
                37 1
                38 1
                39 1
            合并之后的数据
                37 2
                38 2
                39 1
         demo2:
              登录：

             未登录：
                37 1
                38 1
                39 1
                40 1
              合并之后的数据
                37 1
                38 1
                39 1
                40 1
         */
        //1.先获取已登录购物车的数据
        List<CartInfo> cartListLogin = getCartList(userId);
        // 登录购物车数据分为两种状态 一个是有数据可能需要做循环遍历合并，一个没有数据，直接插入数据库
        // 将登录的集合数据转化为map key=skuId,value=cartInfo
        Map<Long, CartInfo> cartInfoMap = cartListLogin.stream()
                .collect(Collectors.toMap(CartInfo::getSkuId, cartInfo -> cartInfo));
        //循环遍历未登录的数据cartInfoNoLoginList
        for (CartInfo cartInfoNoLogin : cartInfoNoLoginList) {
            //获取未登录购物车的skuId
            Long skuId = cartInfoNoLogin.getSkuId();
            // 判断登录的map 集合中key=SkuId，是否包含未登录购物车中的skuId
            if (cartInfoMap.containsKey(skuId)) {
                // 1.第一种情况demo1 登录和未登录有相同的商品 ,将数量相加，相加之后的数据给登录
                CartInfo cartInfoLogin = cartInfoMap.get(skuId);
                cartInfoLogin.setSkuNum(cartInfoLogin.getSkuNum() + cartInfoNoLogin.getSkuNum());

                // 添加一个细节 问题！在合并的时候，我们只处理未登录状态下选中的商品
                //未登录状态选中的商品
                if (cartInfoNoLogin.getIsChecked().intValue() == 1) {
                    // 将购物车中的商品也变为选中状态!
                    cartInfoLogin.setIsChecked(1);
                }
                // 更新数据库
//                    cartInfoMapper.updateById(cartInfoLogin);
                cartAsyncService.updateCartInfo(cartInfoLogin);
            } else {
                // 2.第二种情况demo2 未登录数据在登录中没有或者不存在
                // 未登录中有一个临时用户Id，此时需要将临时用户Id 变为登录用户Id
                cartInfoNoLogin.setUserId(userId);
                //保存到数据库
//                    cartInfoMapper.insert(cartInfoNoLogin);
                cartAsyncService.saveCartInfo(cartInfoNoLogin);
            }
        }
        //合并购物车数据后将数据放入缓存
        List<CartInfo> cartInfoList = loadCartCache(userId);
        //返回合并数据
        return cartInfoList;
    }

    /**
     * 提出方法--删除未登录购物车数据
     * @param userTempId
     */
    private void deleteCartList(String userTempId) {
        // 1.删除数据库
        // delete from userInfo where userId = ?userTempId
//        QueryWrapper<CartInfo> cartInfoQueryWrapper = new QueryWrapper<>();
//        cartInfoQueryWrapper.eq("user_id",userTempId);
//        cartInfoMapper.delete(cartInfoQueryWrapper);
        cartAsyncService.deleteCartInfo(userTempId);

        //2.删除缓存
        //先定义一个未登录的临时key
        String cartKey = getCartKey(userTempId);
        //先判断是否有这个未登录的临时key
        Boolean flag  = redisTemplate.hasKey(cartKey);
        if (flag){
            redisTemplate.delete(cartKey);
        }
    }


    /**
     * 提出方法--根据用户Id获取购物车
     *
     * @param userId
     * @return
     */
    private List<CartInfo> getCartList(String userId) {

        // 声明一个返回的集合对象
        List<CartInfo> cartInfoList = new ArrayList<>();
        // 1.购物车列表先从缓存中获取购物车列表，
        // 2.如果缓存没有，加载数据库，并放入缓存。
//        if (StringUtils.isEmpty(userId)) {
//            return null;
//        }
        if (StringUtils.isEmpty(userId)) return cartInfoList;
        //获取购物车Key
        String cartKey = getCartKey(userId);
        //获取缓存中的数据
        cartInfoList = redisTemplate.opsForHash().values(cartKey);

        // 判断cartInfoList集合中的数据是否存在
        if (!CollectionUtils.isEmpty(cartInfoList)) {
            // 1.说明缓存中有数据
            // 购物车列表显示有顺序：按照商品的更新时间 降序
            // 按照id进行排序
            cartInfoList.sort(new Comparator<CartInfo>() {
                // Comparator 比较器 - 自定义 内名内部类
                @Override
                public int compare(CartInfo o1, CartInfo o2) {
                    return o1.getId().compareTo(o2.getId());
                }
            });
            return cartInfoList;
        } else {
            // 2.说明缓存中没有数据
            // 需要根据用户Id 查询数据库并将数据放入缓存
            cartInfoList = loadCartCache(userId);
            return cartInfoList;
        }
    }

    /**
     * 提出方法--根据用户Id 查询数据库并将数据放入缓存
     *
     * @param userId
     * @return
     */
    public List<CartInfo> loadCartCache(String userId) {

        QueryWrapper<CartInfo> cartInfoQueryWrapper = new QueryWrapper<>();
        cartInfoQueryWrapper.eq("user_id", userId);
        // 1.从数据库得到cartInfoList集合数据
        List<CartInfo> cartInfoList = cartInfoMapper.selectList(cartInfoQueryWrapper);
        //判断获得到的数据是否为空
        if (CollectionUtils.isEmpty(cartInfoList)) {
            //返回空的数据
            return cartInfoList;
        }

        // 2.将数据库中的数据查询并放入缓存
        HashMap<String, CartInfo> map = new HashMap<>();
        for (CartInfo cartInfo : cartInfoList) {
            //查询数据库是因为缓存中没有数据！
            // 有可能会发生价格变动，需要更新价格！
            BigDecimal skuPrice = productFeignClient.getSkuPrice(cartInfo.getSkuId());
            //更新实时价格
            cartInfo.setSkuPrice(skuPrice);
            //map.put(field,value);
            map.put(cartInfo.getSkuId().toString(), cartInfo);
        }
        //获取购物车key
        String cartKey = getCartKey(userId);
        // 将数据库中的数据放入缓存
        redisTemplate.opsForHash().putAll(cartKey, map);
        // 设置过期时间
        setCartKeyExpire(cartKey);
        // 返回数据
        return cartInfoList;
    }


    /**
     * 提出方法--获取用户购物车key
     *
     * @param userId
     * @return
     */
    private String getCartKey(String userId) {
        //定义Key user:userId:cart
        String cartKey = RedisConst.USER_KEY_PREFIX + userId + RedisConst.USER_CART_KEY_SUFFIX;

        return cartKey;
    }

    /**
     * 提出方法--设置过期时间
     *
     * @param cartKey
     */
    private void setCartKeyExpire(String cartKey) {
        redisTemplate.expire(cartKey, RedisConst.USER_CART_EXPIRE, TimeUnit.SECONDS);
    }
}
