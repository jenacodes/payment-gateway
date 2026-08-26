package com.payment.gateway.config;


public record AppConfig(String host, int  dbPort, String dbUsername, String dbPassword, String dbName, int
serverPort) {

}
