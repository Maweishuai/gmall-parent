package com.atguigu.gmall.list.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.list.repository.GoodsRepository;
import com.atguigu.gmall.list.service.SearchService;
import com.atguigu.gmall.model.list.*;
import com.atguigu.gmall.model.product.*;
import com.atguigu.gmall.product.client.ProductFeignClient;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Array;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author mqx
 * @date 2020/6/19 11:48
 */
@Service
public class SearchServiceImpl implements SearchService {
    // 注入service-product-client 对象
    @Autowired
    private ProductFeignClient productFeignClient;

    @Autowired
    private GoodsRepository goodsRepository;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RestHighLevelClient restHighLevelClient;

    @Override
    public void upperGoods(Long skuId) {
        // 声明一个实体类Goods
        Goods goods = new Goods();
        // 商品的基本信息
        SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);
        if (null!=skuInfo){
            goods.setId(skuId);
            goods.setDefaultImg(skuInfo.getSkuDefaultImg());
            goods.setPrice(skuInfo.getPrice().doubleValue());
            goods.setTitle(skuInfo.getSkuName());
            goods.setCreateTime(new Date());

            // 商品的分类信息 传入三级分类Id
            BaseCategoryView categoryView = productFeignClient.getCategoryView(skuInfo.getCategory3Id());
            if (null!=categoryView){
                goods.setCategory1Id(categoryView.getCategory1Id());
                goods.setCategory1Name(categoryView.getCategory1Name());
                goods.setCategory2Id(categoryView.getCategory2Id());
                goods.setCategory2Name(categoryView.getCategory2Name());
                goods.setCategory3Id(categoryView.getCategory3Id());
                goods.setCategory3Name(categoryView.getCategory3Name());
            }

            // 平台属性信息
//            productFeignClient.getAttrList(skuId);
            // skuId = skuInfo.getId();
            List<BaseAttrInfo> attrList = productFeignClient.getAttrList(skuInfo.getId());
            // Function 函数式接口  R apply(T t)
            /*
                SELECT
                    bai.id,
                    bai.attr_name,
                    bai.category_id,
                    bai.category_level,
                    bav.id attr_value_id,
                    bav.value_name,
                    bav.attr_id
                FROM
                    base_attr_info bai
                        INNER JOIN base_attr_value bav ON bai.id = bav.attr_id
                        INNER JOIN sku_attr_value sav ON sav.value_id = bav.id
                WHERE
                        sav.sku_id = 30;
             */
            List<SearchAttr> searchAttrList = attrList.stream().map(baseAttrInfo -> {
                // 通过baseAttrInfo 获取平台属性Id
                SearchAttr searchAttr = new SearchAttr();
                searchAttr.setAttrId(baseAttrInfo.getId());
                searchAttr.setAttrName(baseAttrInfo.getAttrName());
                // 赋值平台属性值名称
                // 获取了平台属性值的集合
                List<BaseAttrValue> attrValueList = baseAttrInfo.getAttrValueList();
                searchAttr.setAttrValue(attrValueList.get(0).getValueName());

                // 将每个平台属性对象searchAttr 返回去
                return searchAttr;
            }).collect(Collectors.toList());
            // 存储平台属性
            if (null!=searchAttrList){
                goods.setAttrs(searchAttrList);
            }
            // 品牌信息
            BaseTrademark trademark = productFeignClient.getTrademark(skuInfo.getTmId());
            if (null!= trademark){
                goods.setTmId(trademark.getId());
                goods.setTmName(trademark.getTmName());
                goods.setTmLogoUrl(trademark.getLogoUrl());
            }
        }
        // 将数据保存到es 中！
        goodsRepository.save(goods);
    }

    @Override
    public void upperGoods() {
        // 读一个一个excel 表格 ,所有要上传的skuId.
    }

    @Override
    public void lowerGoods(Long skuId) {
        // 商品的下架
        goodsRepository.deleteById(skuId);
    }

    @Override
    public void incrHotScore(Long skuId) {
        // 需要借助redis
        String key = "hotScore";
        // 用户每访问一次，那么这个数据应该+1,成员以商品Id 为单位
        Double hotScore = redisTemplate.opsForZSet().incrementScore(key, "skuId:" + skuId, 1);
        // 按照规定来更新es 中的数据 30
        if (hotScore%10==0){
            // 更新一次es 中hotScore
            // 获取到es 中的对象
            Optional<Goods> optional = goodsRepository.findById(skuId);
            // 获取到了当前对象
            Goods goods = optional.get();
            goods.setHotScore(Math.round(hotScore));
            goodsRepository.save(goods);
        }
    }

    @Override
    public SearchResponseVo search(SearchParam searchParam) throws Exception {
        // 构建dsl 语句：利用java 代码来实现一个动态的dsl 语句
        SearchRequest searchRequest = buildQueryDsl(searchParam);
        // 执行dsl 语句
        SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
        // 将查询之后的数据集
        SearchResponseVo responseVo = parseSearchResult(searchResponse);

        // 赋值分页相关的数据属性 设定一个默认值
        responseVo.setPageSize(searchParam.getPageSize());
        responseVo.setPageNo(searchParam.getPageNo());
        // 根据总条数没有显示的多少来计算 {全新的公式计算总页数}
        long totalPages=(responseVo.getTotal()+searchParam.getPageSize()-1)/searchParam.getPageSize();

        // 总页数
        responseVo.setTotalPages(totalPages);

        return responseVo;
    }

    // 获取返回的结果集
    private SearchResponseVo parseSearchResult(SearchResponse searchResponse) {
        // 声明一个对象
        SearchResponseVo searchResponseVo = new SearchResponseVo();
        // private List<SearchResponseTmVo> trademarkList;
        // private List<SearchResponseAttrVo> attrsList = new ArrayList<>();
        // private List<Goods> goodsList = new ArrayList<>();
        // private Long total;//总记录数
        // 获取到品牌信息 {获取agg 中的品牌信息}
        Map<String, Aggregation> aggregationMap = searchResponse.getAggregations().asMap();
        // 获取到了品牌Id 的Agg 获取到桶信息，Aggregation 对象中并没有此方法 ParsedLongTerms
        ParsedLongTerms tmIdAgg = (ParsedLongTerms) aggregationMap.get("tmIdAgg");
        // Aggregation; ctrl + h
        // Function R apply(T t)
        List<SearchResponseTmVo> responseTmVoList = tmIdAgg.getBuckets().stream().map(bucket -> {
            // 声明一个品牌对象
            SearchResponseTmVo searchResponseTmVo = new SearchResponseTmVo();
            // 获取到品牌Id
            String tmId = ((Terms.Bucket) bucket).getKeyAsString();
            searchResponseTmVo.setTmId(Long.parseLong(tmId));

            // 赋值品牌的名称
            Map<String, Aggregation> tmIdAggregationMap = ((Terms.Bucket) bucket).getAggregations().asMap();
            // Aggregation -- ParsedStringTerms
            ParsedStringTerms tmNameAgg = (ParsedStringTerms) tmIdAggregationMap.get("tmNameAgg");
            String tmName = tmNameAgg.getBuckets().get(0).getKeyAsString();
            searchResponseTmVo.setTmName(tmName);

            // 赋值品牌的logoUrl
            ParsedStringTerms tmLogoUrlAgg = (ParsedStringTerms) tmIdAggregationMap.get("tmLogoUrlAgg");
            String tmLogoUrl = tmLogoUrlAgg.getBuckets().get(0).getKeyAsString();
            searchResponseTmVo.setTmLogoUrl(tmLogoUrl);
            // 返回品牌对象
            return searchResponseTmVo;
        }).collect(Collectors.toList());
        // 赋值品牌整个集合数据
        searchResponseVo.setTrademarkList(responseTmVoList);

        // 赋值商品 goodsList
        SearchHits hits = searchResponse.getHits();
        SearchHit[] subHits = hits.getHits();
        // 声明一个商品对象集合
        List<Goods> goodsList = new ArrayList<>();
        if (null!=subHits && subHits.length>0){
            // 循环遍历集合
            for (SearchHit subHit : subHits) {
                // json 字符串
                String sourceAsString = subHit.getSourceAsString();
                // 将json 字符串转化为 goods
                Goods goods = JSON.parseObject(sourceAsString, Goods.class);
                // 获取高亮中的title
                if(null!=subHit.getHighlightFields().get("title")){
                    // 说明title 中有数据，有则获取高亮字段
                    Text title = subHit.getHighlightFields().get("title").getFragments()[0];
                    // 将goods 中的title 进行替换
                    goods.setTitle(title.toString());
                }
                // 将对象添加到集合
                goodsList.add(goods);
            }
        }
        searchResponseVo.setGoodsList(goodsList);

        // 平台属性
        ParsedNested attrAgg = (ParsedNested) aggregationMap.get("attrAgg");
        ParsedLongTerms attrIdAgg = attrAgg.getAggregations().get("attrIdAgg");
        List<? extends Terms.Bucket> buckets = attrIdAgg.getBuckets();
        // 判断集合中是否有数据
        if (!CollectionUtils.isEmpty(buckets)){
            List<SearchResponseAttrVo> responseAttrVoList = buckets.stream().map(bucket -> {
                // 声明一个对象
                SearchResponseAttrVo searchResponseAttrVo = new SearchResponseAttrVo();
                // 赋值属性Id
                searchResponseAttrVo.setAttrId(((Terms.Bucket) bucket).getKeyAsNumber().longValue());
                // 赋值属性名称
                ParsedStringTerms attrNameAgg = ((Terms.Bucket) bucket).getAggregations().get("attrNameAgg");
                String attrName = attrNameAgg.getBuckets().get(0).getKeyAsString();
                searchResponseAttrVo.setAttrName(attrName);

                // 赋值属性值名称
                ParsedStringTerms attrValueAgg = ((Terms.Bucket) bucket).getAggregations().get("attrValueAgg");
                // 属性值可能有多个，循环遍历
                List<? extends Terms.Bucket> valueAggBucketsList = attrValueAgg.getBuckets();
                // 获取到集合中的每个数据
                // Terms.Bucket::getKeyAsString 通过key 来获取value
                List<String> valueList = valueAggBucketsList.stream().map(Terms.Bucket::getKeyAsString).collect(Collectors.toList());
                // 将获取到的属性值放入集合中
                searchResponseAttrVo.setAttrValueList(valueList);

                return searchResponseAttrVo;
            }).collect(Collectors.toList());
            // 将属性，属性值数据放入返回对象
            searchResponseVo.setAttrsList(responseAttrVoList);
        }
        // 赋值总条数
        searchResponseVo.setTotal(hits.totalHits);
        // 返回对象
        return searchResponseVo;
    }

    // 利用java 代码来实现一个动态的dsl语句
    private SearchRequest buildQueryDsl(SearchParam searchParam) {
        // 定义查询器 {}
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        // 构建QueryBuilder {bool }
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        // 判断输入的查询关键字是否为空，构建查询语句
        if (!StringUtils.isEmpty(searchParam.getKeyword())){
            // {must -- match  "title": "小米手机" }
            // Operator.AND 表示查分的词语 在title 中同时存在才会查询数据，如果只存在其中一个是不查询的！
            MatchQueryBuilder title = QueryBuilders.matchQuery("title", searchParam.getKeyword()).operator(Operator.AND);
            // {bool -- must }
            boolQueryBuilder.must(title);
        }
        // 按照分类Id 查询！
        if (null!=searchParam.getCategory1Id()){
            //  { filter -- term "category1Id": "2"}
            TermQueryBuilder category1Id = QueryBuilders.termQuery("category1Id", searchParam.getCategory1Id());
            //  {bool -- filter }
            boolQueryBuilder.filter(category1Id);
        }
        if (null!=searchParam.getCategory2Id()){
            //  { filter -- term "category1Id": "2"}
            TermQueryBuilder category2Id = QueryBuilders.termQuery("category2Id", searchParam.getCategory2Id());
            //  {bool -- filter }
            boolQueryBuilder.filter(category2Id);
        }
        if (null!=searchParam.getCategory3Id()){
            //  { filter -- term "category1Id": "2"}
            TermQueryBuilder category3Id = QueryBuilders.termQuery("category3Id", searchParam.getCategory3Id());
            //  {bool -- filter }
            boolQueryBuilder.filter(category3Id);
        }
        // 查询品牌！判断用户是否输入了品牌查询条件  查询参数应该是这样的：【trademark=2:华为】
        // 获取用户查询的品牌数据
        String trademark = searchParam.getTrademark();
        if (!StringUtils.isEmpty(trademark)){
            // 用户输入了品牌查询 通过key 获取值 2:华为 ，将value 进行分割
            // split[0] = 2 split[1] = 华为
            String[] split = trademark.split(":");
            // 判断数据格式是否正确
            if (null!=split && split.length==2){
                //  { filter -- term "tmId": "4"}
                TermQueryBuilder tmId = QueryBuilders.termQuery("tmId", split[0]);
                //  {bool -- filter }
                boolQueryBuilder.filter(tmId);
            }
        }
        // 根据用户的平台属性值 进行查询！
        // 判断用户是否进行了平台属性值过滤
        // http://list.gmall.com/list.html?category3Id=61&props=1:2800-4499:价格&props=2:6.75-6.84英寸:屏幕尺寸&order=
        String[] props = searchParam.getProps();
        if (null!=props && props.length>0){
            // 数值中的数据是什么样的？ props=23:4G:运行内存
            // 循环遍历props
            for (String prop : props) {
                // 对当前的数据进行分割
                String[] split = prop.split(":");
                // 判断数据格式 props=23:4G:运行内存
                if (null!=split && split.length==3){
                    // 如何对平台属性值进行过滤的？
                    // 创建一个又一个 bool 对象
                    BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
                    BoolQueryBuilder subBoolQuery = QueryBuilders.boolQuery();
                    // {bool - must - term}
                    subBoolQuery.must(QueryBuilders.termQuery("attrs.attrId", split[0]));
                    // 根据属性值过滤
                    subBoolQuery.must(QueryBuilders.termQuery("attrs.attrValue", split[1]));

                    // 开始嵌套
                    boolQuery.must(QueryBuilders.nestedQuery("attrs",subBoolQuery, ScoreMode.None));

                    // 整合查询
                    boolQueryBuilder.filter(boolQuery);
                }
            }
        }
        // {query}
        searchSourceBuilder.query(boolQueryBuilder);

        // 分页设置：
        // 计算每页开始的起始条数
        int from = (searchParam.getPageNo()-1)*searchParam.getPageSize();
        searchSourceBuilder.from(from);
        searchSourceBuilder.size(searchParam.getPageSize());

        // 设置高亮
        HighlightBuilder highlightBuilder = new HighlightBuilder();
        highlightBuilder.field("title");
        highlightBuilder.postTags("</span>");
        highlightBuilder.preTags("<span style=color:red>");
        // 设置好的高亮对象放入方法中
        searchSourceBuilder.highlighter(highlightBuilder);

        // 做排序
        // 先获取用户是否点击了排序功能
        String order = searchParam.getOrder();
        if (!StringUtils.isEmpty(order)){
            // 页面传递的时候：&order=1：asc || &order=1：desc
            // 1=hotScore 2=price
            // 对数据进行分割
            String[] split = order.split(":");
            // 判断一下格式
            if (null!=split && split.length==2){
                // 声明一个field 字段 用它来记录按照谁进行排序
                String field = null;
                switch (split[0]){
                    case "1":
                        field = "hotScore";
                        break;
                    case "2":
                        field = "price";
                        break;
                }
                // 设置排序规则  &order=1：asc || &order=1：desc
                searchSourceBuilder.sort(field, "asc".equals(split[1])?SortOrder.ASC:SortOrder.DESC);
                // 排序规则应该按照：页面传递过来的规则，并应该写死！
                // searchSourceBuilder.sort(field, SortOrder.ASC);
            }else {
                // order=1: 默认排序规则
                searchSourceBuilder.sort("hotScore", SortOrder.DESC);
            }
        }

        // 设置品牌聚合
        TermsAggregationBuilder termsAggregationBuilder = AggregationBuilders.terms("tmIdAgg").field("tmId")
                .subAggregation(AggregationBuilders.terms("tmNameAgg").field("tmName"))
                .subAggregation(AggregationBuilders.terms("tmLogoUrlAgg").field("tmLogoUrl"));

        // 将品牌的agg 放入查询器
        searchSourceBuilder.aggregation(termsAggregationBuilder);
        // tmIdAgg 普通字段，attrAgg 是内嵌聚合
        // 设置平台属性聚合
        searchSourceBuilder.aggregation(AggregationBuilders.nested("attrAgg", "attrs")
                .subAggregation(AggregationBuilders.terms("attrIdAgg").field("attrs.attrId")
                        .subAggregation(AggregationBuilders.terms("attrNameAgg").field("attrs.attrName"))
                        .subAggregation(AggregationBuilders.terms("attrValueAgg").field("attrs.attrValue"))));

        // 数据结果集进行过滤 查询数据的时候，结果集显示 "id","defaultImg","title","price" 对应的数据
        searchSourceBuilder.fetchSource(new String[]{"id","defaultImg","title","price"},null);

        //指定index，type GET /goods/info/_search {}
        SearchRequest searchRequest = new SearchRequest("goods");
        searchRequest.types("info");
        searchRequest.source(searchSourceBuilder);
        // 打印dsl 语句
        System.out.println("dsl:"+searchSourceBuilder.toString());
        return searchRequest;
    }
}
