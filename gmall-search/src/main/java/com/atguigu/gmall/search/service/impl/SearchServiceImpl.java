package com.atguigu.gmall.search.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.pms.entity.BrandEntity;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import com.atguigu.gmall.search.entity.Goods;
import com.atguigu.gmall.search.entity.SearchParamVo;
import com.atguigu.gmall.search.entity.SearchResponseAttrVo;
import com.atguigu.gmall.search.entity.SearchResponseVo;
import com.atguigu.gmall.search.service.SearchService;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class SearchServiceImpl implements SearchService {

    @Autowired
    private RestHighLevelClient restHighLevelClient;

    @Override
    public SearchResponseVo search(SearchParamVo paramVo) {
        try {
            SearchRequest request = new SearchRequest(new String[] {"goods"},buildDsl(paramVo));
            SearchResponse searchResponse = this.restHighLevelClient.search(request, RequestOptions.DEFAULT);
            //TODO: 解析结果集
            SearchResponseVo responseVo = this.parseResult(searchResponse);
            responseVo.setPageNum(paramVo.getPageNum());
            responseVo.setPageSize(paramVo.getPageSize());
            return responseVo;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void search1(SearchParamVo paramVo) {
        try {
            SearchRequest request = new SearchRequest(new String[] {"goods"},buildDsl(paramVo));
            SearchResponse searchResponse = this.restHighLevelClient.search(request, RequestOptions.DEFAULT);
            //TODO: 解析结果集
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 解析搜索结果集
     * @param response
     * @return
     */
    private SearchResponseVo parseResult(SearchResponse response) {
        SearchResponseVo responseVo = new SearchResponseVo();
        SearchHits hits = response.getHits();
        //总命中记录数
        responseVo.setTotal(hits.getTotalHits());
        SearchHit[] hitsHits = hits.getHits();
        List<Goods> goodsList = Stream.of(hitsHits).map(hitsHit -> {
            // 获取内层hits的_source 数据
            String goodsJson = hitsHit.getSourceAsString();
            // 反序列化为goods对象
            Goods goods = JSON.parseObject(goodsJson, Goods.class);

            // 获取高亮的title覆盖掉普通title
            Map<String, HighlightField> highlightFields = hitsHit.getHighlightFields();
            HighlightField highlightField = highlightFields.get("title");
            String highlightTitle = highlightField.getFragments()[0].toString();
            goods.setTitle(highlightTitle);
            return goods;
        }).collect(Collectors.toList());
        responseVo.setGoodsList(goodsList);

        // 聚合结果集的解析
        Map<String, Aggregation> aggregationMap = response.getAggregations().asMap();
        // 1. 解析聚合结果集，获取品牌
        // {attrId: null, attrName: "品牌"， attrValues: [{id: 1, name: 尚硅谷, logo: http://www.atguigu.com/logo.gif}, {}]}
        ParsedLongTerms brandIdAgg = (ParsedLongTerms)aggregationMap.get("brandIdAgg");
        List<? extends Terms.Bucket> buckets = brandIdAgg.getBuckets();
        if (!CollectionUtils.isEmpty(buckets)){
            List<BrandEntity> brands = buckets.stream().map(bucket -> { // {id: 1, name: 尚硅谷, logo: http://www.atguigu.com/logo.gif}
                // 为了得到指定格式的json字符串，创建了一个map
                BrandEntity brandEntity = new BrandEntity();
                // 获取brandIdAgg中的key，这个key就是品牌的id
                Long brandId = ((Terms.Bucket) bucket).getKeyAsNumber().longValue();
                brandEntity.setId(brandId);
                // 解析品牌名称的子聚合，获取品牌名称
                Map<String, Aggregation> brandAggregationMap = ((Terms.Bucket) bucket).getAggregations().asMap();
                ParsedStringTerms brandNameAgg = (ParsedStringTerms)brandAggregationMap.get("brandNameAgg");
                brandEntity.setName(brandNameAgg.getBuckets().get(0).getKeyAsString());
                // 解析品牌logo的子聚合，获取品牌 的logo
                ParsedStringTerms logoAgg = (ParsedStringTerms)brandAggregationMap.get("logoAgg");
                List<? extends Terms.Bucket> logoAggBuckets = logoAgg.getBuckets();
                if (!CollectionUtils.isEmpty(logoAggBuckets)){
                    brandEntity.setLogo(logoAggBuckets.get(0).getKeyAsString());
                }
                // 把map反序列化为json字符串
                return brandEntity;
            }).collect(Collectors.toList());
            responseVo.setBrands(brands);
        }

        // 2. 解析聚合结果集，获取分类
        ParsedLongTerms categoryIdAgg = (ParsedLongTerms)aggregationMap.get("categoryIdAgg");
        List<? extends Terms.Bucket> categoryIdAggBuckets = categoryIdAgg.getBuckets();
        if (!CollectionUtils.isEmpty(categoryIdAggBuckets)){
            List<CategoryEntity> categories = categoryIdAggBuckets.stream().map(bucket -> { // {id: 225, name: 手机}
                CategoryEntity categoryEntity = new CategoryEntity();
                // 获取bucket的key，key就是分类的id
                Long categoryId = ((Terms.Bucket) bucket).getKeyAsNumber().longValue();
                categoryEntity.setId(categoryId);
                // 解析分类名称的子聚合，获取分类名称
                ParsedStringTerms categoryNameAgg = (ParsedStringTerms)((Terms.Bucket) bucket).getAggregations().get("categoryNameAgg");
                categoryEntity.setName(categoryNameAgg.getBuckets().get(0).getKeyAsString());
                return categoryEntity;
            }).collect(Collectors.toList());
            responseVo.setCategories(categories);
        }

        // 3. 解析聚合结果集，获取规格参数
        //ParsedNested attrAgg = (ParsedNested) aggregationMap.get("attrAgg");
        //获取嵌套集合中的子聚合
//        ParsedLongTerms attrIdAgg = (ParsedLongTerms)attrAgg.getAggregations().get("attrIdAgg");
//        List<? extends Terms.Bucket> attrIdAggBuckets = attrIdAgg.getBuckets();
//        if (!CollectionUtils.isEmpty(attrIdAggBuckets)){
//            responseVo.setFilters(attrIdAggBuckets.stream().map(bucket -> {
//                SearchResponseAttrVo searchResponseAttrVo = new SearchResponseAttrVo();
//                searchResponseAttrVo.setAttrId(((Terms.Bucket)bucket).getKeyAsNumber().longValue());
//                //获取attrIdAgg的子聚合
//                Map<String, Aggregation> subAggregationMap = ((Terms.Bucket) bucket).getAggregations().asMap();
//                //获取规格参数名称的子聚合
//                ParsedStringTerms attrNameAgg = (ParsedStringTerms) subAggregationMap.get("attrNameAgg");
//                List<? extends Terms.Bucket> nameAggBuckets = attrNameAgg.getBuckets();
//                if (!CollectionUtils.isEmpty(nameAggBuckets)){
//                    searchResponseAttrVo.setAttrName(nameAggBuckets.get(0).getKeyAsString());
//                }
//                ParsedStringTerms attrValueAgg = (ParsedStringTerms) subAggregationMap.get("attrValueAgg");
//                List<? extends Terms.Bucket> valueAggBuckets = attrValueAgg.getBuckets();
//                if (!CollectionUtils.isEmpty(valueAggBuckets)){
//                    searchResponseAttrVo.setAttrValues(buckets.stream()
//                            .map(Terms.Bucket::getKeyAsString).collect(Collectors.toList()));
//                }
//                return searchResponseAttrVo;
//            }).collect(Collectors.toList()));
//        }
        ParsedNested attrAgg = (ParsedNested)aggregationMap.get("attrAgg");
        ParsedLongTerms attrIdAgg = (ParsedLongTerms)attrAgg.getAggregations().get("attrIdAgg");
        List<? extends Terms.Bucket> attrIdAggBuckets = attrIdAgg.getBuckets();
        if (!CollectionUtils.isEmpty(attrIdAggBuckets)) {
            List<SearchResponseAttrVo> filters = attrIdAggBuckets.stream().map(bucket -> {
                SearchResponseAttrVo responseAttrVo = new SearchResponseAttrVo();
                // 规格参数id
                responseAttrVo.setAttrId(((Terms.Bucket) bucket).getKeyAsNumber().longValue());
                // 规格参数的名称
                ParsedStringTerms attrNameAgg = (ParsedStringTerms)((Terms.Bucket) bucket).getAggregations().get("attrNameAgg");
                responseAttrVo.setAttrName(attrNameAgg.getBuckets().get(0).getKeyAsString());
                // 规格参数值
                ParsedStringTerms attrValueAgg = (ParsedStringTerms)((Terms.Bucket) bucket).getAggregations().get("attrValueAgg");
                List<? extends Terms.Bucket> attrValueAggBuckets = attrValueAgg.getBuckets();
                if (!CollectionUtils.isEmpty(attrValueAggBuckets)){
                    List<String> attrValues = attrValueAggBuckets.stream().map(Terms.Bucket::getKeyAsString).collect(Collectors.toList());
                    responseAttrVo.setAttrValues(attrValues);
                }
                return responseAttrVo;
            }).collect(Collectors.toList());
            responseVo.setFilters(filters);
        }
        return responseVo;
    }

    private SearchSourceBuilder buildDsl(SearchParamVo paramVo) {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        String keyword = paramVo.getKeyword();
        if (StringUtils.isBlank(keyword)){
            //TODO: 打广告
            return sourceBuilder;
        }
        //1.构建查询过滤条件
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        sourceBuilder.query(boolQueryBuilder);
        //1.1.构建查询条件
        boolQueryBuilder.must(QueryBuilders.matchQuery("title",keyword).operator(Operator.AND));
        //1.2.构建过滤条件
        //1.2.1 品牌过滤
        List<Long> brandId = paramVo.getBrandId();
        if (!CollectionUtils.isEmpty(brandId)){
            boolQueryBuilder.filter(QueryBuilders.termsQuery("brandId",brandId));
        }
        //1.2.2 分类过滤
        Long categoryId = paramVo.getCategoryId();
        if (categoryId != null){
            boolQueryBuilder.filter(QueryBuilders.termQuery("categoryId",categoryId));
        }
        //1.2.3 价格区间过滤
        Double priceFrom = paramVo.getPriceFrom();
        Double priceTo = paramVo.getPriceTo();
        if (priceFrom != null || priceTo != null){
            RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery("price");
            if (priceFrom != null){
                rangeQuery.gte(priceFrom);
            }
            if (priceTo != null){
                rangeQuery.lte(priceTo);
            }
            boolQueryBuilder.filter(rangeQuery);
        }
        //1.2.4 是否有货
        Boolean store = paramVo.getStore();
        if (store!=null){
            boolQueryBuilder.filter(QueryBuilders.termQuery("store",store));
        }
       //1.2.5 规格参数过滤
        List<String> props = paramVo.getProps();
        if (!CollectionUtils.isEmpty(props)){
            props.forEach(prop -> {//4:8G-12G
                String[] attr = StringUtils.split(prop, ":");
                if (attr != null && attr.length == 2){
                    BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
                    //规格参数id单词条查询
                    boolQuery.must(QueryBuilders.termQuery("searchAttrs.attrId",attr[0]));
                    //规格参数值得多条件查询
                    String[] attrValues = StringUtils.split(attr[1],"-");
                    boolQuery.must(QueryBuilders.termsQuery("searchAttrs.attrValue",attrValues));
                    boolQueryBuilder.filter(QueryBuilders.nestedQuery("searchAttrs",boolQuery, ScoreMode.None));
                }
            });
        }
        sourceBuilder.query(boolQueryBuilder);
        //2.排序  0.综合排序 1.价格降序 2.价格升序 3.销量降序 4.新品降序
        Integer sort = paramVo.getSort();
        if (sort != null){
            switch (sort){
                case 1: sourceBuilder.sort("price", SortOrder.DESC); break;
                case 2: sourceBuilder.sort("price", SortOrder.ASC); break;
                case 3: sourceBuilder.sort("sales",SortOrder.DESC); break;
                case 4: sourceBuilder.sort("createTime",SortOrder.DESC); break;
                default:
                    break;
            }
        }

        //3.分页
        Integer pageNum = paramVo.getPageNum();
        Integer pageSize = paramVo.getPageSize();
        sourceBuilder.from((pageNum - 1) * pageSize);
        sourceBuilder.size(pageSize);
        //4.高亮
        sourceBuilder.highlighter(new HighlightBuilder()
                .field("title").preTags("<font style='color:red'>").postTags("</font>"));
        //5.聚合
        //5.1. 品牌聚合
        sourceBuilder.aggregation(AggregationBuilders.terms("brandIdAgg").field("brandId")
                .subAggregation(AggregationBuilders.terms("brandNameAgg").field("brandName"))
                .subAggregation(AggregationBuilders.terms("logoAgg").field("logo")));
        //5.2. 分类聚合
        sourceBuilder.aggregation(AggregationBuilders.terms("categoryIdAgg").field("categoryId")
                .subAggregation(AggregationBuilders.terms("categoryNameAgg").field("categoryName")));
        //5.3. 规格参数的嵌套聚合
        sourceBuilder.aggregation(AggregationBuilders.nested("attrAgg","searchAttrs")
                .subAggregation(AggregationBuilders.terms("attrIdAgg").field("searchAttrs.attrId")
                .subAggregation(AggregationBuilders.terms("attrNameAgg").field("searchAttrs.attrName"))
                .subAggregation(AggregationBuilders.terms("attrValueAgg").field("searchAttrs.attrValue"))
        ));
        // 6. 构建结果集过滤
        sourceBuilder.fetchSource(new String[]{"skuId", "title", "price", "defaultImage"},null);
//        System.out.println("sourceBuilder = " + sourceBuilder);
        return sourceBuilder;
    }
}
