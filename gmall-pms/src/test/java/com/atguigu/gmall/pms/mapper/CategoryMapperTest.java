package com.atguigu.gmall.pms.mapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CategoryMapperTest {

    @Autowired
    CategoryMapper categoryMapper;
    @Test
    void queryCategoriesWithSubByPid() {
        System.out.println(this.categoryMapper.queryCategoriesWithSubByPid(1l));
    }
}