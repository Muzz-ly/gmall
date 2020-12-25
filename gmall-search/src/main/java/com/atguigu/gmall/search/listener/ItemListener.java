package com.atguigu.gmall.search.listener;

import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.search.entity.Goods;
import com.atguigu.gmall.search.entity.SearchAttrValue;
import com.atguigu.gmall.search.feign.GmallPmsClient;
import com.atguigu.gmall.search.feign.GmallWmsClient;
import com.atguigu.gmall.search.repository.GoodsRepository;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ItemListener {

    @Autowired
    private GmallPmsClient pmsClient;
    @Autowired
    private GmallWmsClient wmsClient;
    @Autowired
    private GoodsRepository goodsRepository;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "SEARCH_ITEM_QUEUE",durable = "true"),//durable是否持久化
            exchange = @Exchange(value = "PMS_ITEM_EXCHANGE",ignoreDeclarationExceptions = "true",type = ExchangeTypes.TOPIC),
            key = {"item.insert"}
    ))
    public void listener(Long spuId){
        if (spuId == null){
            return;
        }
        ResponseVo<List<SkuEntity>> skuResponseVo = this.pmsClient.querySkuBySpuId(spuId);
        List<SkuEntity> skuEntities = skuResponseVo.getData();
        if (!CollectionUtils.isEmpty(skuEntities)){
            //3.把sku集合装成goods集合
            List<Goods> goodsList = skuEntities.stream().map(skuEntity -> {
                //设置sku相关
                Goods goods = new Goods();
                goods.setSkuId(skuEntity.getId());
                goods.setTitle(skuEntity.getTitle());
                goods.setSubTitle(skuEntity.getSubtitle());
                goods.setDefaultImage(skuEntity.getDefaultImage());
                goods.setPrice(skuEntity.getPrice().doubleValue());

                ResponseVo<SpuEntity> spuEntity = pmsClient.querySpuById(spuId);
                Date createTime = spuEntity.getData().getCreateTime();
                if (createTime != null){
                    //设置spu相关
                    goods.setCreateTime(createTime);
                }

                //设置库存相关
                ResponseVo<List<WareSkuEntity>> wareResponseVo = this.wmsClient.queryWareSkuBySkuId(skuEntity.getId());
                List<WareSkuEntity> wareSkuEntities = wareResponseVo.getData();
                if (!CollectionUtils.isEmpty(wareSkuEntities)) {
                    Long sales = wareSkuEntities.stream().map(WareSkuEntity::getSales).reduce((a, b) -> a + b).get();
                    goods.setSales(sales);
                    boolean b = wareSkuEntities.stream().anyMatch(wareSkuEntity ->
                            //判断仓库是否有货,所有仓库减去锁定的数量 > 0 则认为有货
                            wareSkuEntity.getStock() - wareSkuEntity.getStockLocked() > 0);
                    goods.setStore(b);
                }

                //设置 品牌
                ResponseVo<BrandEntity> brandEntityResponseVo = this.pmsClient.queryBrandById(skuEntity.getBrandId());
                BrandEntity brandEntity = brandEntityResponseVo.getData();
                if (brandEntity != null) {
                    goods.setBrandId(brandEntity.getId());
                    goods.setBrandName(brandEntity.getName());
                    goods.setLogo(brandEntity.getLogo());
                }

                //设置分类
                ResponseVo<CategoryEntity> categoryEntityResponseVo = this.pmsClient.
                        queryCategoryById(skuEntity.getCatagoryId());
                CategoryEntity categoryEntity = categoryEntityResponseVo.getData();
                if (categoryEntity != null) {
                    goods.setCategoryId(categoryEntity.getId());
                    goods.setCategoryName(categoryEntity.getName());
                }

                //设置其他筛选条件
                //获取检索参数
                List<SearchAttrValue> searchAttrValues = new ArrayList<>();
                //查询销售类型的检索参数
                ResponseVo<List<SkuAttrValueEntity>> searchAttrValueSkuResponseVo = this.pmsClient.
                        querySearchAttrValueBySkuId(skuEntity.getId());
                List<SkuAttrValueEntity> skuAttrValueEntities = searchAttrValueSkuResponseVo.getData();
                //将查询到的sku信息转换为searchAttrValue的参数类型
                if (!CollectionUtils.isEmpty(skuAttrValueEntities)) {
                    searchAttrValues.addAll(skuAttrValueEntities.stream().map(skuAttrValueEntity -> {
                        SearchAttrValue searchAttrValue = new SearchAttrValue();
                        BeanUtils.copyProperties(skuAttrValueEntity, searchAttrValue);
                        return searchAttrValue;
                    }).collect(Collectors.toList()));
                }
                //查询基本类型的检索参数
                ResponseVo<List<SpuAttrValueEntity>> searchAttrValueSpuResponseVo = this.pmsClient.
                        querySearchAttrValueBySpuId(spuId);
                List<SpuAttrValueEntity> spuAttrValueEntities = searchAttrValueSpuResponseVo.getData();
                if (!CollectionUtils.isEmpty(spuAttrValueEntities)) {
                    searchAttrValues.addAll(
                            spuAttrValueEntities.stream().map(
                                    spuAttrValueEntity -> {
                                        SearchAttrValue searchAttrValue = new SearchAttrValue();
                                        BeanUtils.copyProperties(spuAttrValueEntity, searchAttrValue);
                                        return searchAttrValue;
                                    }
                            ).collect(Collectors.toList())
                    );
                }
                goods.setSearchAttrs(searchAttrValues);
                return goods;
            }).collect(Collectors.toList());
            this.goodsRepository.saveAll(goodsList);
        }
    }
}
