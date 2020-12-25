package com.atguigu.gmall.pms.api;

import com.atguigu.gmall.common.bean.PageParamVo;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.pms.entity.*;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

public interface GmallPmsApi {
    @PostMapping("pms/spu/page")
    public ResponseVo<List<SpuEntity>> queryStoreSpuByPage(@RequestBody PageParamVo pageParamVo);

    @GetMapping("pms/sku/spu/{spuId}")
    public ResponseVo<List<SkuEntity>> querySkuBySpuId(@PathVariable("spuId")Long spuId);

    @GetMapping("pms/category/{id}")
    public ResponseVo<CategoryEntity> queryCategoryById(@PathVariable("id") Long id);

    @GetMapping("pms/brand/{id}")
    public ResponseVo<BrandEntity> queryBrandById(@PathVariable("id") Long id);

    @GetMapping("pms/spuattrvalue/spu/{spuId}")
    public ResponseVo<List<SpuAttrValueEntity>> querySearchAttrValueBySpuId(@PathVariable("spuId")Long spuId);

    @GetMapping("pms/skuattrvalue/sku/{skuId}")
    public ResponseVo<List<SkuAttrValueEntity>> querySearchAttrValueBySkuId(@PathVariable("skuId")Long skuId);

    @GetMapping("pms/spu/{id}")
    @ApiOperation("详情查询")
    public ResponseVo<SpuEntity> querySpuById(@PathVariable("id") Long id);
}
