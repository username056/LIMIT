package com.c203.limit.global.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "limit.bootstrap.demo-data")
public class DemoDataProperties {
    private int userCount = 5;
    private int productsPerUser = 4;
    private String password;
    private String emailDomain = "limit.local";

    public int getUserCount() {
        return userCount;
    }

    public void setUserCount(int userCount) {
        this.userCount = userCount;
    }

    public int getProductsPerUser() {
        return productsPerUser;
    }

    public void setProductsPerUser(int productsPerUser) {
        this.productsPerUser = productsPerUser;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmailDomain() {
        return emailDomain;
    }

    public void setEmailDomain(String emailDomain) {
        this.emailDomain = emailDomain;
    }
}
