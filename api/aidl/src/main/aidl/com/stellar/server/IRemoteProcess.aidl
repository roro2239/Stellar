package com.stellar.server;

interface IRemoteProcess {

    ParcelFileDescriptor getOutputStream();

    ParcelFileDescriptor getInputStream();

    ParcelFileDescriptor getErrorStream();

    int waitFor();

    int exitValue();

    void destroy();

    boolean alive();

    boolean waitForTimeout(long timeout, String unit);
}

