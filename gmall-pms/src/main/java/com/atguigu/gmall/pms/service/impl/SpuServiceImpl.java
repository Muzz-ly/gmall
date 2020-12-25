package com.atguigu.gmall.pms.service.impl;

import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.pms.feign.GmallSmsClient;
import com.atguigu.gmall.pms.mapper.SkuMapper;
import com.atguigu.gmall.pms.mapper.SpuDescMapper;
import com.atguigu.gmall.pms.service.SkuAttrValueService;
import com.atguigu.gmall.pms.service.SkuImagesService;
import com.atguigu.gmall.pms.service.SpuAttrValueService;
import com.atguigu.gmall.pms.vo.SkuVo;
import com.atguigu.gmall.pms.vo.SpuAttrValueVo;
import com.atguigu.gmall.pms.vo.SpuVo;
import com.atguigu.gmall.sms.vo.SkuSaleVo;
import io.seata.spring.annotation.GlobalTransactional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.pms.mapper.SpuMapper;
import com.atguigu.gmall.pms.service.SpuService;
import org.springframework.util.CollectionUtils;


@Service("spuService")
public class SpuServiceImpl extends ServiceImpl<SpuMapper, SpuEntity> implements SpuService {

    @Autowired
    private SpuDescMapper descMapper;
    @Autowired
    private SpuAttrValueService baseService;
    @Autowired
    private SkuMapper skuMapper;
    @Autowired
    private SkuAttrValueService skuAttrValueService;
    @Autowired
    private SkuImagesService skuImagesService;
    @Autowired
    private GmallSmsClient smsClient;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<SpuEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<SpuEntity>()
        );

        return new PageResultVo(page);
    }

    @Override
    public PageResultVo querySpuByCidAndPage(Long cid, PageParamVo paramVo) {
        QueryWrapper<SpuEntity> wrapper = new QueryWrapper<>();
        //如果用户选择了分类,并且查询本类
        if (cid != 0){
            wrapper.eq("category_id",cid);
        }
        String key = paramVo.getKey();
        //判断关键字是否为空
        if (StringUtils.isNotBlank(key)){
            wrapper.and(t ->t.eq("id",key).or().like("name",key));
        }
        IPage<SpuEntity> page = this.page(paramVo.getPage(), wrapper);
        return new PageResultVo(page);
    }

    @GlobalTransactional
    @Override
    public void bigSave(SpuVo spuVo) {
        /// 1.保存spu相关
        // 1.1. 保存spu基本信息 spu_info
        spuVo.setPublishStatus(1);//默认已上架
        spuVo.setCreateTime(new Date());
        spuVo.setUpdateTime(spuVo.getCreateTime());//新增时,更新和创建时间一致
        this.save(spuVo);
        Long spuId = spuVo.getId();//获取新增的spuId
        // 1.2. 保存spu的描述信息 spu_info_desc
        SpuDescEntity spuDescEntity = new SpuDescEntity();
        // 注意：spu_info_desc表的主键是spu_id,需要在实体类中配置该主键不是自增主键
        spuDescEntity.setSpuId(spuId);
        // 把商品的图片描述，保存到spu详情中，图片地址以逗号进行分割
        spuDescEntity.setDecript(StringUtils.join(spuVo.getSpuImages(),","));
        this.descMapper.insert(spuDescEntity);

        //1.3. 保存spu的规格参数信息
        List<SpuAttrValueVo> baseAttrs = spuVo.getBaseAttrs();
        if (!CollectionUtils.isEmpty(baseAttrs)){
            List<SpuAttrValueEntity> spuAttrValueEntities =
                    baseAttrs.stream().map(spuAttrValueVo -> {
                        spuAttrValueVo.setSpuId(spuId);
                        spuAttrValueVo.setSort(0);
                        return spuAttrValueVo;
                    }).collect(Collectors.toList());
            this.baseService.saveBatch(spuAttrValueEntities);
        }

        //2.保存数据库相关信息
        List<SkuVo> skuVos = spuVo.getSkus();
        if (CollectionUtils.isEmpty(skuVos)){
            return ;
        }
        skuVos.forEach(skuVo -> {
            //2.1保存sku基本信息
            SkuEntity skuEntity = new SkuEntity();
            BeanUtils.copyProperties(skuVo,skuEntity);
            //品牌和分类的id需要从spuInfo中获取
            skuEntity.setBrandId(spuVo.getBrandId());
            skuEntity.setCatagoryId(spuVo.getCategoryId());
            //获取图片列表地址
            List<String> images = skuVo.getImages();
            //如果图片列表不为null，则设置一张为默认图片
            if (!CollectionUtils.isEmpty(images)){
                skuEntity.setDefaultImage(skuEntity.getDefaultImage()
                ==null ? images.get(0):skuEntity.getDefaultImage());
            }
            skuEntity.setSpuId(spuId);
            this.skuMapper.insert(skuEntity);
            //获取skuId
            Long skuId = skuEntity.getId();

            //2.2. 保存sku图片信息
            if (!CollectionUtils.isEmpty(images)){
                String defaultImage = images.get(0);
                List<SkuImagesEntity> skuImages =
                        images.stream().map(image ->{
                            SkuImagesEntity skuImagesEntity = new SkuImagesEntity();
                            skuImagesEntity.setDefaultStatus(
                                    StringUtils.equals(defaultImage,image)?1:0);
                            skuImagesEntity.setSkuId(skuId);
                            skuImagesEntity.setSort(0);
                            skuImagesEntity.setUrl(image);
                            return skuImagesEntity;
                        }).collect(Collectors.toList());
                this.skuImagesService.saveBatch(skuImages);
            }
            // 2.3. 保存sku的规格参数（销售属性）
            List<SkuAttrValueEntity> saleAttrs = skuVo.getSaleAttrs();
            if (!CollectionUtils.isEmpty(saleAttrs)){

                saleAttrs.forEach(saleAttr ->{
                    // 设置属性名，需要根据id查询AttrEntity
                    saleAttr.setSort(0);
                    saleAttr.setSkuId(skuId);
                });
                this.skuAttrValueService.saveBatch(saleAttrs);
            }
            // 3. 保存营销相关信息，需要远程调用gmall-sms
            // 3.1. 积分优惠
            SkuSaleVo skuSaleVo = new SkuSaleVo();
            BeanUtils.copyProperties(skuVo,skuSaleVo);
            skuSaleVo.setSkuId(skuId);
            this.smsClient.saveSkuSaleInfo(skuSaleVo);
            // 3.2. 满减优惠

            // 3.3. 数量折扣
        });

        //rabbitMQ作通知,新增商品的时候会有通知
        this.rabbitTemplate.convertAndSend("PMS_ITEM_EXCHANGE","item.insert",spuId);
    }

//    private void saveBaseAttr(SpuVo spuVo, Long spuId) {
//        List<SpuAttrValueVo> baseAttrs = spuVo.getBaseAttrs();
//        if (!CollectionUtils.isEmpty(baseAttrs)) {
//            List<SpuAttrValueEntity> spuAttrValueEntities = baseAttrs.stream().map(spuAttrValueVO -> {
//                spuAttrValueVO.setSpuId(spuId);
//                spuAttrValueVO.setSort(0);
//                return spuAttrValueVO;
//            }).collect(Collectors.toList());
//            this.baseService.saveBatch(spuAttrValueEntities);
//        }
//    }
//
//    private void saveSpuDesc(SpuVo spuVo, Long spuId) {
//        SpuDescEntity spuInfoDescEntity = new SpuDescEntity();
//        // 注意：spu_info_desc表的主键是spu_id,需要在实体类中配置该主键不是自增主键
//        spuInfoDescEntity.setSpuId(spuId);
//        // 把商品的图片描述，保存到spu详情中，图片地址以逗号进行分割
//        spuInfoDescEntity.setDecript(StringUtils.join(spuVo.getSpuImages(), ","));
//        this.descMapper.insert(spuInfoDescEntity);
//    }
//
//    private Long saveSpu(SpuVo spuVo) {
//        spuVo.setPublishStatus(1); // 默认是已上架
//        spuVo.setCreateTime(new Date());
//        spuVo.setUpdateTime(spuVo.getCreateTime()); // 新增时，更新时间和创建时间一致
//        this.save(spuVo);
//         return spuVo.getId();
//    }

}