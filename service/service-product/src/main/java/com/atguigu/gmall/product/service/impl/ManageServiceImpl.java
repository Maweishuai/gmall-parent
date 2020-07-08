package com.atguigu.gmall.product.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.client.naming.utils.CollectionUtils;
import com.atguigu.gmall.common.cache.GmallCache;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.model.product.*;
import com.atguigu.gmall.product.mapper.*;
import com.atguigu.gmall.product.service.ManageService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author Administrator
 * @create 2020-06-09 23:11
 */
@Service
public class ManageServiceImpl implements ManageService {

    @Autowired
    private BaseCategory1Mapper baseCategory1Mapper;

    @Autowired
    private BaseCategory2Mapper baseCategory2Mapper;

    @Autowired
    private BaseCategory3Mapper baseCategory3Mapper;

    @Autowired
    private BaseAttrInfoMapper baseAttrInfoMapper;

    @Autowired
    private BaseAttrValueMapper baseAttrValueMapper;

    @Autowired
    private SpuInfoMapper spuInfoMapper;

    @Autowired
    private BaseSaleAttrMapper baseSaleAttrMapper;

    @Autowired
    private SpuImageMapper spuImageMapper;

    @Autowired
    private SpuSaleAttrMapper spuSaleAttrMapper;

    @Autowired
    private SpuSaleAttrValueMapper spuSaleAttrValueMapper;

    @Autowired
    private SkuInfoMapper skuInfoMapper;

    @Autowired
    private SkuAttrValueMapper skuAttrValueMapper;

    @Autowired
    private SkuImageMapper skuImageMapper;

    @Autowired
    private SkuSaleAttrValueMapper skuSaleAttrValueMapper;

    @Autowired
    private BaseCategoryViewMapper baseCategoryViewMapper;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private BaseTrademarkMapper baseTrademarkMapper;

    @Autowired
    private RabbitService rabbitService;

    @Override
    public List<BaseCategory1> getCategory1() {
        // select * from base_category1; 表与实体类与mapper 名称对应！
        return baseCategory1Mapper.selectList(null);
    }

    @Override
    public List<BaseCategory2> getCategory2(Long category1Id) {
        // select * from base_category2 where category1_id=category1Id;
        QueryWrapper<BaseCategory2> baseCategory2QueryWrapper = new QueryWrapper<>();
        baseCategory2QueryWrapper.eq("category1_id", category1Id);
        return baseCategory2Mapper.selectList(baseCategory2QueryWrapper);
    }

    @Override
    public List<BaseCategory3> getCategory3(Long category2Id) {
        // select * from base_category3 where category2_id=category2Id;
        QueryWrapper<BaseCategory3> baseCategory3QueryWrapper = new QueryWrapper<>();
        baseCategory3QueryWrapper.eq("category2_id", category2Id);
        return baseCategory3Mapper.selectList(baseCategory3QueryWrapper);
    }

    @Override
    public List<BaseAttrInfo> getArrInfoList(Long category1Id, Long category2Id, Long category3Id) {

        /*
        *根据分类Id 和层级关系 查询平台属性。
        * select * from base_attr_info where category_id=category1Id and category_level = 1 or
         select * from base_attr_info where category_id=category2Id and category_level = 2 or
         select * from base_attr_info where category_id=category3Id and category_level = 3 or
        *
        * 扩展功能：
        * 如果我们再根据分类id查询得到平台属性时，又能得到属性值就更好了！
        *我们可以将base_attr_info表与base_attr_value表做关联，
        * 关联条件：info.id=value.attr_id
        *
        * */
        return baseAttrInfoMapper.selectBaseAttrInfoList(category1Id, category2Id, category3Id);
    }

    @Override
    @Transactional
    public void saveAttrInfo(BaseAttrInfo baseAttrInfo) {
        //我们要在这个方法中添加两个功能，一个是添加数据，一个是修改数据
        //一、先对Info属性表进行操作
        //判断回显的属性值表是否有属性id，如果有就是修改，否则添加
        //从页面功能看，修改属性表的同时也可以对属性值表进行修改（包括新增和删除）
        if (null != baseAttrInfo.getId()) {
            baseAttrInfoMapper.updateById(baseAttrInfo);
        } else {
            baseAttrInfoMapper.insert(baseAttrInfo);
        }
        //二、再对value属性值表进行操作
        //  修改：通过先1.删除{baseAttrValue}，在2.新增的方式！
        //  对属性值表可以进行删除和添加，还有修改，
        //  这时我们可以对属性值数据修改后进行整体删除，然后再整体保存
        //1.整体删除属性值数据集合
        QueryWrapper<BaseAttrValue> baseAttrValueQueryWrapper = new QueryWrapper<>();
        //条件删除：根据传递过来的平台属性值Id 进行删除
        //删除条件：baseAttrValue.attrId = baseAttrInfo.id
        baseAttrValueQueryWrapper.eq("attr_id", baseAttrInfo.getId());
        //将之前属性值集合全部删除
        baseAttrValueMapper.delete(baseAttrValueQueryWrapper);

        //2.整体保存属性值数据集合
        // 平台属性值插入的时候，可能存在多个值的去情况，具体是多少个值，需要看传递过来的数据。
        // 页面在传递平台属性值数据的时候，数据会自动封装到 BaseAttrInfo 中 这个属性中 attrValueList
        //  前台页面给封装好的！
        // 获取页面传递过来的所有平台属性值数据
        List<BaseAttrValue> attrValueList = baseAttrInfo.getAttrValueList();
        if (null != attrValueList && attrValueList.size() > 0) {
            for (BaseAttrValue baseAttrValue : attrValueList) {
                // 页面在提交数据的时候，并没有给attrId 赋值，所以在此处需要手动赋值
                // attrId = baseAttrInfo.getId();
                // 获取平台属性Id 赋值给attrId
                baseAttrValue.setAttrId(baseAttrInfo.getId());
                // 循环将数据添加到数据表中
                baseAttrValueMapper.insert(baseAttrValue);
            }
        }

    }

    @Override
    public BaseAttrInfo getAttrInfo(Long attrId) {
        //根据平台属性ID获取平台属性
        //通过attrId先得到平台属性Info,再通过条件attrId 得到平台属性值集合
        BaseAttrInfo baseAttrInfo = baseAttrInfoMapper.selectById(attrId);

        //创建wrapper条件对象
        QueryWrapper<BaseAttrValue> baseAttrValueQueryWrapper = new QueryWrapper<>();
        baseAttrValueQueryWrapper.eq("attr_id", attrId);
        List<BaseAttrValue> baseAttrValueList = baseAttrValueMapper.selectList(baseAttrValueQueryWrapper);

        //将得到的属性值集合valueList放入平台属性中
        baseAttrInfo.setAttrValueList(baseAttrValueList);
        //将平台属性（包括平台属性值数据）返回给controller
        return baseAttrInfo;


    }

    @Override
    public IPage<SpuInfo> selectPage(Page<SpuInfo> pageParam, SpuInfo spuInfo) {

        //封装查询条件
        QueryWrapper<SpuInfo> spuInfoQueryWrapper = new QueryWrapper<>();
        spuInfoQueryWrapper.eq("category3_id", spuInfo.getCategory3Id());
        spuInfoQueryWrapper.orderByDesc("id");//查询之后，按照一定规则排序

        //将根据条件查询的数据返回
        return spuInfoMapper.selectPage(pageParam, spuInfoQueryWrapper);
    }

    @Override
    public List<BaseSaleAttr> getBaseSaleAttrList() {
        //查询所有销售属性数据
        List<BaseSaleAttr> baseSaleAttrList = baseSaleAttrMapper.selectList(null);

        return baseSaleAttrList;
    }

    /*
       * 保存所有spu的数据，包括以下四个表的数据
       * spuInfo 表中的数据
         spuImage 图片列表
         spuSaleAttr 销售属性
         spuSaleAttrValue 销售属性值
       */
    @Override
    @Transactional
    public void saveSpuInfo(SpuInfo spuInfo) {
        //1.spuInfo 商品表
        spuInfoMapper.insert(spuInfo);
        //2.spuImage 商品图片表
        List<SpuImage> spuImageList = spuInfo.getSpuImageList();
        if (null != spuImageList && spuImageList.size() > 0) {
            for (SpuImage spuImage : spuImageList) {
                spuImage.setSpuId(spuInfo.getId());
                spuImageMapper.insert(spuImage);
            }
        }
        //3.spuSaleAttr 销售属性表
        List<SpuSaleAttr> spuSaleAttrList = spuInfo.getSpuSaleAttrList();
        if (null != spuSaleAttrList && spuSaleAttrList.size() > 0) {
            for (SpuSaleAttr spuSaleAttr : spuSaleAttrList) {
                spuSaleAttr.setSpuId(spuInfo.getId());
                spuSaleAttrMapper.insert(spuSaleAttr);

                //4.spuSaleAttrValue 销售属性值表 在销售属性中获取销售属性值
                List<SpuSaleAttrValue> spuSaleAttrValueList = spuSaleAttr.getSpuSaleAttrValueList();
                if (null != spuSaleAttrValueList && spuSaleAttrValueList.size() > 0)
                    for (SpuSaleAttrValue spuSaleAttrValue : spuSaleAttrValueList) {
                        spuSaleAttrValue.setSpuId(spuInfo.getId());
                        spuSaleAttrValue.setSaleAttrName(spuSaleAttr.getSaleAttrName());
                        spuSaleAttrValueMapper.insert(spuSaleAttrValue);
                    }
            }
        }
    }

    @Override
    public List<SpuImage> getSpuImageList(Long spuId) {

        QueryWrapper<SpuImage> spuImageQueryWrapper = new QueryWrapper<>();
        spuImageQueryWrapper.eq("spu_id", spuId);

        return spuImageMapper.selectList(spuImageQueryWrapper);
    }

    @Override
    public List<SpuSaleAttr> spuSaleAttrList(Long spuId) {

        return spuSaleAttrMapper.selectSpuSaleAttrList(spuId);
    }

    @Override
    @Transactional
    public void saveSkuInfo(SkuInfo skuInfo) {
        /*
        skuInfo 库存单元表 --- spuInfo！
        skuImage 库存单元图片表 --- spuImage!
        skuSaleAttrValue sku销售属性值表{sku与销售属性值的中间表} --- skuInfo ，spuSaleAttrValue
        skuAttrValue sku与平台属性值的中间表 --- skuInfo ，baseAttrValue
     */
        //1.skuInfo 库存单元表 --- spuInfo
        skuInfoMapper.insert(skuInfo);

        //2.skuImage 库存单元图片表 --- spuImage
        List<SkuImage> skuImageList = skuInfo.getSkuImageList();
        if (null != skuImageList && skuImageList.size() > 0) {
            for (SkuImage skuImage : skuImageList) {
                skuImage.setSkuId(skuInfo.getId());
                skuImageMapper.insert(skuImage);
            }
        }

        //3.skuSaleAttrValue sku销售属性值表{sku与销售属性值的中间表} --- skuInfo ，spuSaleAttrValue
        List<SkuSaleAttrValue> skuSaleAttrValueList = skuInfo.getSkuSaleAttrValueList();
        if (!CollectionUtils.isEmpty(skuSaleAttrValueList)) {
            for (SkuSaleAttrValue skuSaleAttrValue : skuSaleAttrValueList) {

                System.out.println(skuSaleAttrValue);
                skuSaleAttrValue.setSkuId(skuInfo.getId());
                skuSaleAttrValue.setSpuId(skuInfo.getSpuId());
                skuSaleAttrValueMapper.insert(skuSaleAttrValue);
            }
        }


        //4.skuAttrValue sku与平台属性值的中间表 --- skuInfo ，baseAttrValue
        List<SkuAttrValue> skuAttrValueList = skuInfo.getSkuAttrValueList();
        if (!CollectionUtils.isEmpty(skuAttrValueList)) {
            for (SkuAttrValue skuAttrValue : skuAttrValueList) {
                skuAttrValue.setSkuId(skuInfo.getId());
                skuAttrValueMapper.insert(skuAttrValue);
            }
        }

        // 商品上架  发送一个消息队列，发送的内容就是skuId 发送到es上
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_GOODS,
                MqConst.ROUTING_GOODS_UPPER,skuInfo.getId());
    }

    @Override
    public IPage<SkuInfo> selectPage(Page<SkuInfo> pageParam) {

        QueryWrapper<SkuInfo> skuInfoQueryWrapper = new QueryWrapper<>();
        skuInfoQueryWrapper.orderByDesc("id");
        return skuInfoMapper.selectPage(pageParam, skuInfoQueryWrapper);
    }

    @Override
    @Transactional
    public void onSale(Long skuId) {
        // 更改销售状态
        //  is_sale = 1 表示可以上架，
        // update sku_info set is_sale = 1 where id=skuId
        SkuInfo skuInfo = new SkuInfo();
        skuInfo.setId(skuId);
        skuInfo.setIsSale(1);
        skuInfoMapper.updateById(skuInfo);

        // 商品上架  发送一个消息队列，发送的内容就是skuId 发送到es上
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_GOODS,
                MqConst.ROUTING_GOODS_UPPER,skuId);
    }

    @Override
    public void cancelSale(Long skuId) {
        // 更改销售状态
        // 0 那么则这商品不能买！
        // update sku_info set is_sale = 0 where id=skuId
        SkuInfo skuInfo = new SkuInfo();
        skuInfo.setId(skuId);
        skuInfo.setIsSale(0);
        skuInfoMapper.updateById(skuInfo);

        // 商品下架  发送一个消息队列，发送的内容就是skuId 发送到es上
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_GOODS,
                MqConst.ROUTING_GOODS_LOWER,skuId);
    }

    // 使用redission做分布式锁
    private SkuInfo getSkuInfoRedisson(Long skuId) {
        // 在此获取skuInfo 的时候，先查询缓存，如果缓存中有数据，则查询，没有查询数据库并放入缓存!
        SkuInfo skuInfo = null;
        try {
            // 先判断缓存中是否有数据，查询缓存必须知道缓存的key是什么！
            // 定义缓存的key 商品详情的缓存key=sku:skuId:info
            String skuKey = RedisConst.SKUKEY_PREFIX+skuId+RedisConst.SKUKEY_SUFFIX;
            // 根据key 获取缓存中的数据
            // 如果查询一个不存在的数据，那么缓存中应该是一个空对象{这个对象有地址，但是属性Id，price 等没有值}
            skuInfo = (SkuInfo) redisTemplate.opsForValue().get(skuKey);
            // 存储数据为什么使用String ，存储对象的时候建议使用Hash---{hset(skuKey,字段名,字段名所对应的值); 便于对当前对象中属性修改}
            // 对于商品详情来讲：我们只做显示，并没有修改。所以此处可以使用String 来存储!
            if (skuInfo==null){
                // 从数据库中获取数据，防止缓存击穿做分布式锁
                // 定义分布式锁的key lockKey=sku:skuId:lock
                String lockKey = RedisConst.SKUKEY_PREFIX+skuId+RedisConst.SKULOCK_SUFFIX;
                // 使用redisson
                RLock lock = redissonClient.getLock(lockKey);
                // 尝试加锁，最多等待100秒，上锁以后10秒自动解锁
                boolean res = lock.tryLock(RedisConst.SKULOCK_EXPIRE_PX2, RedisConst.SKULOCK_EXPIRE_PX1, TimeUnit.SECONDS);
                if (res) {
                    try {
                        // 从数据库中获取数据
                        skuInfo = getSkuInfoDB(skuId);
                        if (skuInfo==null){
                            // 为了防止缓存穿透，设置一个空对象放入缓存,这个时间建议不要太长！
                            SkuInfo skuInfo1 = new SkuInfo();
                            // 放入缓存
                            redisTemplate.opsForValue().set(skuKey,skuInfo1,RedisConst.SKUKEY_TEMPORARY_TIMEOUT,TimeUnit.SECONDS);
                            // 返回数据
                            return  skuInfo1;
                        }
                        // 从数据库中获取到了数据，放入缓存
                        redisTemplate.opsForValue().set(skuKey,skuInfo,RedisConst.SKUKEY_TIMEOUT,TimeUnit.SECONDS);
                        return skuInfo;
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        // 解锁
                        lock.unlock();
                    }
                }else {
                    // 此时的线程并没有获取到分布式锁，应该等待,
                    Thread.sleep(1000);
                    // 等待完成之后，还需要查询数据！
                    return getSkuInfo(skuId);
                }
            }else {
                // 表示缓存中有数据了
                // 弯！稍加严禁一点：
                //            if (skuInfo.getId()==null){ // 这个对象有地址，但是属性Id，price 等没有值！
                //                return null;
                //            }
                // 缓存中有数据，应该直接返回即可！
                return skuInfo; // 情况一：这个对象有地址，但是属性Id，price 等没有值！  情况二：就是既有地址，又有属性值！

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 如何中途发送了异常：数据库挺一下！
        return  getSkuInfoDB(skuId);
    }

    private SkuInfo getSkuInfoRedis(Long skuId) {
        // 在此获取skuInfo 的时候，先查询缓存，如果缓存中有数据，则查询，没有查询数据库并放入缓存!
        SkuInfo skuInfo = null;
        try {
            // 先判断缓存中是否有数据，查询缓存必须知道缓存的key是什么！
            // 定义缓存的key 商品详情的缓存key=sku:skuId:info
            String skuKey = RedisConst.SKUKEY_PREFIX+skuId+RedisConst.SKUKEY_SUFFIX;
            // 根据key 获取缓存中的数据
            // 如果查询一个不存在的数据，那么缓存中应该是一个空对象{这个对象有地址，但是属性Id，price 等没有值}
            skuInfo = (SkuInfo) redisTemplate.opsForValue().get(skuKey);
            // 存储数据为什么使用String ，存储对象的时候建议使用Hash---{hset(skuKey,字段名,字段名所对应的值); 便于对当前对象中属性修改}
            // 对于商品详情来讲：我们只做显示，并没有修改。所以此处可以使用String 来存储!
            if (skuInfo==null){
                // 应该获取数据库中的数据，放入缓存！分布式锁！为了防止缓存击穿
                // 定义分布式锁的key lockKey=sku:skuId:lock
                String lockKey = RedisConst.SKUKEY_PREFIX+skuId+RedisConst.SKULOCK_SUFFIX;
                // 还需要一个uuId，做为锁的值value
                String uuid= UUID.randomUUID().toString();
                // 开始上锁
                Boolean isExist = redisTemplate.opsForValue().setIfAbsent(lockKey, uuid, RedisConst.SKULOCK_EXPIRE_PX1, TimeUnit.SECONDS);
                // 如果返回true 获取到分布式锁！
                if (isExist){
                    System.out.println("获取到锁！");
                    // 去数据库获取数据，并放入缓存！
                    // 传入的skuId 在数据库中一定存在么？
                    skuInfo = getSkuInfoDB(skuId);
                    if (skuInfo==null){
                        // 为了防止缓存穿透，设置一个空对象放入缓存,这个时间建议不要太长！
                        SkuInfo skuInfo1 = new SkuInfo();
                        // 放入缓存
                        redisTemplate.opsForValue().set(skuKey,skuInfo1,RedisConst.SKUKEY_TEMPORARY_TIMEOUT,TimeUnit.SECONDS);
                        // 返回数据
                        return  skuInfo1;
                    }
                    // 从数据库中查询出来不是空！放入缓存
                    redisTemplate.opsForValue().set(skuKey,skuInfo,RedisConst.SKUKEY_TIMEOUT,TimeUnit.SECONDS);
                    // 删除锁！ 使用lua 脚本删除！
                    String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
                    // 如何操作：
                    // 构建RedisScript 数据类型需要确定一下，默认情况下返回的Object
                    DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
                    // 指定好返回的数据类型
                    redisScript.setResultType(Long.class);
                    // 指定好lua 脚本
                    redisScript.setScriptText(script);
                    // 第一个参数存储的RedisScript  对象，第二个参数指的锁的key，第三个参数指的key所对应的值
                    redisTemplate.execute(redisScript, Arrays.asList(lockKey),uuid);

                    // 返回正常数据
                    return skuInfo;
                }else {
                    // 此时的线程并没有获取到分布式锁，应该等待,
                    Thread.sleep(1000);
                    // 等待完成之后，还需要查询数据！
                    return getSkuInfo(skuId);
                }
            }else {
                // 弯！稍加严禁一点：
                //            if (skuInfo.getId()==null){ // 这个对象有地址，但是属性Id，price 等没有值！
                //                return null;
                //            }
                // 缓存中有数据，应该直接返回即可！
                return skuInfo; // 情况一：这个对象有地址，但是属性Id，price 等没有值！  情况二：就是既有地址，又有属性值！
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // 为了防止缓存宕机，用数据库兜底来查询
        return  getSkuInfoDB(skuId);
    }

    //提取方法，表示此方法用来从数据库查询获取数据
    private SkuInfo getSkuInfoDB(Long skuId) {
        //1.Sku基本信息
        SkuInfo skuInfo = skuInfoMapper.selectById(skuId);
        //2.Sku图片信息
        if (null != skuInfo) {
            QueryWrapper<SkuImage> skuImageQueryWrapper = new QueryWrapper<>();
            skuImageQueryWrapper.eq("sku_id", skuId);
            List<SkuImage> skuImageList = skuImageMapper.selectList(skuImageQueryWrapper);
            skuInfo.setSkuImageList(skuImageList);
        }
        return skuInfo;
    }

    @GmallCache(prefix = "sku")
    @Override
    public SkuInfo getSkuInfo(Long skuId) {
//      return getSkuInfoRedis(skuId);

        // 使用框架redisson解决分布式锁！
        return getSkuInfoRedisson(skuId);
    }

    @GmallCache(prefix = "categoryView")
    @Override
    public BaseCategoryView getCategoryView(Long category3Id) {
        //3.Sku分类信息
        return baseCategoryViewMapper.selectById(category3Id);

    }
    @GmallCache(prefix = "skuPrice")
    @Override
    public BigDecimal getSkuPrice(Long skuId) {
        //4.Sku价格信息
        SkuInfo skuInfo = skuInfoMapper.selectById(skuId);
        if (null != skuInfo) {
            return skuInfo.getPrice();
        }
        return new BigDecimal(0);
    }

    //5.1Sku销售属性相关信息
    //回显销售属性，销售属性值并锁定！
    @GmallCache(prefix = "spuSaleAttrListCheck")
    @Override
    public List<SpuSaleAttr> getSpuSaleAttrListCheckBySku(Long skuId, Long spuId) {
        return spuSaleAttrMapper.selectSpuSaleAttrListCheckBySku(skuId, spuId);
    }

    //5.2Sku销售属性相关信息
    //用户点击其他销售属性值时，跳转到不同的sku
    @GmallCache(prefix = "SkuValueIdsMap")
    @Override
    public Map getSkuValueIdsMap(Long spuId) {
        // 调用mapper 自定义方法获取数据，将数据查询之后直接放入List。
        HashMap<Object, Object> map = new HashMap<>();
        /*
            select sv.sku_id, group_concat(sv.sale_attr_value_id order by sp.base_sale_attr_id asc separator '|')
                value_ids from sku_sale_attr_value sv
                inner  join spu_sale_attr_value  sp on sp.id = sv.sale_attr_value_id
                where sv.spu_id = 12
                group by sku_id;

            执行出来的结果应该是List<Map>
            map.put("55|57","30") skuSaleAttrValueMapper
         */
        List<Map> mapList = skuSaleAttrValueMapper.getSaleAttrValuesBySpu(spuId);
        // 获取到数据以后。开始循环遍历集合中的每条数据
        if (null != mapList && mapList.size() > 0) {
            for (Map skuMap : mapList) {
                map.put(skuMap.get("value_ids"), skuMap.get("sku_id"));
            }
        }
        return map;
    }

    //实现首页商品分类展示
    @Override
    public List<JSONObject> getBaseCategoryList() {

        //获取所有分类数据集合
        List<BaseCategoryView> baseCategoryViewList = baseCategoryViewMapper.selectList(null);
        //创建一个整体数据的json 最后将一级二级三级分类数据，都放入此list返回
        ArrayList<JSONObject> list = new ArrayList<>();
        //循环上面的集合并安一级分类Id 进行分组
        Map<Long, List<BaseCategoryView>> category1Map = baseCategoryViewList.stream().collect(Collectors.groupingBy(BaseCategoryView::getCategory1Id));
        // 初始化一个index 构建json 字符串 "index": 1
        int index = 1;

        // 循环遍历，获取一级分类下所有数据
        for (Map.Entry<Long,List<BaseCategoryView>> entry1: category1Map.entrySet()) {
            // 获取一级分类Id
            Long category1Id = entry1.getKey();
            // 获取一级分类下面的所有集合
            List<BaseCategoryView> category2List1 = entry1.getValue();
            // 声明一级分类JSON对象
            JSONObject category1= new JSONObject();
            //保存一级分类数据
            category1.put("index",index);
            //一级分类Id
            category1.put("categoryId",category1Id);
            //一级分类名称
            category1.put("categoryName",category2List1.get(0).getCategory1Name());
            // 变量迭代
            index++;

            // 声明二级分类对象集合
            ArrayList<JSONObject> category2Child = new ArrayList<>();
            // 循环获取二级分类数据
            Map<Long, List<BaseCategoryView>> category2Map = category2List1.stream().collect(Collectors.groupingBy(BaseCategoryView::getCategory2Id));
            // 循环遍历，获取二级分类下所有数据
            for (Map.Entry<Long,List<BaseCategoryView>> entry2 : category2Map.entrySet()) {
                // 获取二级分类Id
                Long category2Id = entry2.getKey();
                // 获取二级分类下面的所有集合
                List<BaseCategoryView> category3List = entry2.getValue();
                // 声明二级分类JSON对象
                JSONObject category2= new JSONObject();
                // 二级分类Id
                category2.put("categoryId",category2Id);
                // 二级分类名称
                category2.put("categoryName",category3List.get(0).getCategory2Name());
                // 添加到二级分类集合
                category2Child.add(category2);

                // 声明三级分类对象集合
                ArrayList<JSONObject> category3Child = new ArrayList<>();
                category3List.stream().forEach(category3View ->{
                    // 声明三级分类JSON对象
                    JSONObject category3 = new JSONObject();
                    category3.put("categoryId",category3View.getCategory3Id());
                    category3.put("categoryName",category3View.getCategory3Name());
                    // 添加到三级分类集合
                    category3Child.add(category3);
                });
                // 将三级数据放入二级里面
                category2.put("categoryChild",category3Child);
            }
            // 将二级数据放入一级里面
            category1.put("categoryChild",category2Child);
            // 将所有数据放入list
            list.add(category1);
        }
        return list;
    }
    //根据品牌id获取品牌数据
    @Override
    public BaseTrademark getTrademarkByTmId(Long tmId) {

        return baseTrademarkMapper.selectById(tmId);

    }

    //通过skuId 集合来查询数据
    @Override
    public List<BaseAttrInfo> getAttrList(Long skuId) {

        return baseAttrInfoMapper.selectBaseAttrInfoListBySkuId(skuId);

    }

}
