package com.atguigu.gmall.index.service;

import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.index.config.GmallCache;
import com.atguigu.gmall.index.feign.GmallPmsClient;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class IndexService {

    @Autowired
    private GmallPmsClient pmsClient;

    public List<CategoryEntity> queryCategoriesByPid(){
        ResponseVo<List<CategoryEntity>> listResponseVo = this.pmsClient.queryCategoryByPid(0l);
        return listResponseVo.getData();
    }

    @GmallCache(prefix = "index:cates:", timeout = 14400, random = 3600, lock = "lock:")
    public List<CategoryEntity> queryCategoriesWithSubsByPid(Long pid){
        ResponseVo<List<CategoryEntity>> listResponseVo = this.pmsClient.queryCategoryWithSubByPid(pid);
        return listResponseVo.getData();
    }
}
