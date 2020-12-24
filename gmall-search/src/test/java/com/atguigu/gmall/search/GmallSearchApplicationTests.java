package com.atguigu.gmall.search;

import com.atguigu.gmall.common.bean.PageParamVo;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.search.entity.Goods;
import com.atguigu.gmall.search.entity.SearchAttrValue;
import com.atguigu.gmall.search.feign.GmallPmsClient;
import com.atguigu.gmall.search.feign.GmallWmsClient;
import com.atguigu.gmall.search.repository.GoodsRepository;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@SpringBootTest
class GmallSearchApplicationTests {

    @Autowired
    private GoodsRepository goodsRepository;
    @Autowired
    private GmallPmsClient pmsClient;
    @Autowired
    private GmallWmsClient wmsClient;

    @Test
    void contextLoads() {

        Integer pageNum = 1;
        Integer pageSize = 100;

        do {
            //1.分批查询spu
            PageParamVo pageParamVo = new PageParamVo();
            pageParamVo.setPageNum(pageNum);
            pageParamVo.setPageSize(pageSize);
            ResponseVo<List<SpuEntity>> responseVo = this.pmsClient.queryStoreSpuByPage(pageParamVo);
            List<SpuEntity> spuEntities = responseVo.getData();

            //2.遍历当前页的spu,查询出spu下的所有sku
            if (CollectionUtils.isEmpty(spuEntities)){
                break;
            }
            spuEntities.forEach(spuEntity -> {
                ResponseVo<List<SkuEntity>> skuResponseVo = this.pmsClient.querySkuBySpuId(spuEntity.getId());
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

                        //设置spu相关
                        goods.setCreateTime(spuEntity.getCreateTime());

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
                                querySearchAttrValueBySpuId(spuEntity.getId());
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
            });
            pageNum++;
            pageSize = spuEntities.size();
        } while (pageSize == 100);
    }

}
