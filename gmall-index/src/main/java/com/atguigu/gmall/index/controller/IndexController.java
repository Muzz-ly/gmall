package com.atguigu.gmall.index.controller;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.index.service.IndexService;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Controller
public class IndexController {

    @Autowired
    private IndexService indexService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "index:cates:";

    @GetMapping({"index.html","/"})
    public String toIndex(Model model){

        //三级分类数据
        List<CategoryEntity> categoryEntities = this.indexService.queryCategoriesByPid();
        model.addAttribute("categories",categoryEntities);
        
        //TODO 打广告
        return "index";
    }

    @GetMapping("index/cates/{pid}")
    @ResponseBody
    public ResponseVo<List<CategoryEntity>> queryCategoriesWithSubs(@PathVariable("pid")Long pid ){
        //先查缓存
        String cacheCates = this.redisTemplate.opsForValue().get(KEY_PREFIX + pid);
        if (StringUtils.isNotBlank(cacheCates)){
            //如果缓存中有,则直接返回
            List<CategoryEntity> categoryEntities = JSON.parseArray(cacheCates, CategoryEntity.class);
            return ResponseVo.ok(categoryEntities);
        }

        List<CategoryEntity> categoryEntities = indexService.queryCategoriesWithSubsByPid(pid);
        //查完放回缓存
        this.redisTemplate.opsForValue().set(KEY_PREFIX + pid, JSON.toJSONString(categoryEntities), 30 + new Random().nextInt(10),TimeUnit.DAYS);
        System.out.println("categoryEntities = " + categoryEntities);
        return ResponseVo.ok(categoryEntities);
    }
}
