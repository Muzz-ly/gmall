package com.atguigu.gmall.pms.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CategoryServiceTest {

    @Autowired
    CategoryService categoryService;

    @Test
    void queryCategoryWithSubByPid() {
        this.categoryService.queryCategoryWithSubByPid(1l);
    }
}