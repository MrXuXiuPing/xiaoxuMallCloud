package com.mallplus;

import com.mallplus.common.annotation.EnableLoginArgResolver;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * @author 作者 mallplus E-mail: 951449465@qq.com
 */
//@EnableScheduling//开启定时任务
@ServletComponentScan
@EnableLoginArgResolver
@EnableDiscoveryClient
@EnableFeignClients
@EnableTransactionManagement
@SpringBootApplication
public class GoodsCenterApp {
    public static void main(String[] args) {
        SpringApplication.run(GoodsCenterApp.class, args);
    }
}
