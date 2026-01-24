package com.stellar.server;

interface IStellarApplication {

    oneway void bindApplication(in Bundle data) = 1;

    oneway void dispatchRequestPermissionResult(int requestCode, in Bundle data) = 2;
}
