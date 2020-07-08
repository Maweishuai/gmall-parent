package com.atguigu.gmall.list.repository;

import com.atguigu.gmall.model.list.Goods;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * @author Administrator
 * @create 2020-06-20 0:55
 */
public interface GoodsRepository extends ElasticsearchRepository<Goods,Long> {


}
