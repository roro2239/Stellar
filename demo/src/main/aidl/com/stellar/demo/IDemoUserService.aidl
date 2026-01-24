package com.stellar.demo;

interface IDemoUserService {
    String executeCommand(String command) = 1;

    String getSystemProperty(String name) = 2;
}
