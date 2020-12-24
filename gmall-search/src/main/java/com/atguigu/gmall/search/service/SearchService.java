package com.atguigu.gmall.search.service;

import com.atguigu.gmall.search.entity.SearchParamVo;
import com.atguigu.gmall.search.entity.SearchResponseVo;
import org.springframework.stereotype.Service;

public interface SearchService {
    SearchResponseVo search(SearchParamVo paramVo);
    void search1(SearchParamVo paramVo);
}
