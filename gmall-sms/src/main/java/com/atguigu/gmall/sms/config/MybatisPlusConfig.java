package com.atguigu.gmall.sms.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.PaginationInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.optimize.JsqlParserCountOptimize;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
/*
* **使用mybatis-plus步骤：**
1. **配置目录中引入映射文件**
2. **在启动类上添加@MapperScan扫描所有mapper/dao接口**
3. **编写mapper接口实现BaseMapper<T>即可**
4. **编写service接口继承IService<T>，编写xxxService继承ServiceImpl<Mapper,  Entity>**
5. **查询分页要添加分页过滤器：**
* */
//Spring boot方式
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor paginationInterceptor() {
        MybatisPlusInterceptor paginationInterceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor innerInterceptor = new PaginationInnerInterceptor();
        // 设置请求的页面大于最大页后操作， true调回到首页，false 继续请求  默认false
        // paginationInterceptor.setOverflow(false);
        // 设置最大单页限制数量，默认 500 条，-1 不受限制
        // paginationInterceptor.setLimit(500);
        // 开启 count 的 join 优化,只针对部分 left join
        paginationInterceptor.setInterceptors(Arrays.asList(innerInterceptor));
        return paginationInterceptor;
    }
}
