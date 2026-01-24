package com.stellar.server;

interface IUserServiceCallback {
    oneway void onServiceConnected(in IBinder service, String verificationToken) = 1;

    oneway void onServiceDisconnected() = 2;

    oneway void onServiceStartFailed(int errorCode, String message) = 3;
}
