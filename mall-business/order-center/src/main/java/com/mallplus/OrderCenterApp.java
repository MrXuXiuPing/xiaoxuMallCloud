package com.mallplus;

import com.mallplus.common.annotation.EnableLoginArgResolver;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.util.StopWatch;

/**
 * @author 作者 mallplus E-mail: 951449465@qq.com
 */
@ServletComponentScan
@EnableLoginArgResolver
@EnableDiscoveryClient
@EnableFeignClients
@EnableTransactionManagement
@SpringBootApplication
public class OrderCenterApp {
    public static void main(String[] args){
        SpringApplication.run(OrderCenterApp.class, args);
    }
}
