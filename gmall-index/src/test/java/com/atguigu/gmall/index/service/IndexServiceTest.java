package com.atguigu.gmall.index.service;

import com.atguigu.gmall.index.controller.IndexController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class IndexServiceTest {

    @Autowired
    private IndexService indexService;
    @Test
    void queryCategoriesWithSubsByPid() {
        System.out.println(this.indexService.queryCategoriesWithSubsByPid(1l));
    }
}