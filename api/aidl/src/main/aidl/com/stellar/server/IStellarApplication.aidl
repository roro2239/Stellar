package com.stellar.server;

// Stellar 应用回调接口
interface IStellarApplication {

    // 绑定应用回调
    oneway void bindApplication(in Bundle data) = 1;

    // 分发权限请求结果
    oneway void dispatchRequestPermissionResult(int requestCode, in Bundle data) = 2;
}
