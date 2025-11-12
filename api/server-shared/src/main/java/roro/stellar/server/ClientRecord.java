package roro.stellar.server;

import static roro.stellar.StellarApiConstants.REQUEST_PERMISSION_REPLY_ALLOWED;

import android.os.Bundle;

import com.stellar.server.IStellarApplication;
import roro.stellar.server.util.Logger;

/**
 * 客户端记录类
 * Client Record Class
 * 
 * <p>功能说明 Features：</p>
 * <ul>
 * <li>存储已连接客户端的信息 - Stores information of connected clients</li>
 * <li>管理客户端的权限状态 - Manages client permission status</li>
 * <li>处理权限请求结果的分发 - Handles permission request result dispatch</li>
 * </ul>
 * 
 * <p>包含信息 Information：</p>
 * <ul>
 * <li>UID和PID - 客户端进程标识</li>
 * <li>包名和API版本 - 客户端应用信息</li>
 * <li>权限状态 - 是否授权及一次性授权标志</li>
 * </ul>
 */
public class ClientRecord {

    protected static final Logger LOGGER = new Logger("ClientRecord");

    /** 客户端UID Client UID */
    public final int uid;
    
    /** 客户端PID Client PID */
    public final int pid;
    
    /** 客户端回调接口 Client callback interface */
    public final IStellarApplication client;
    
    /** 客户端包名 Client package name */
    public final String packageName;
    
    /** 客户端API版本 Client API version */
    public final int apiVersion;
    
    /** 是否已授权 Whether authorized */
    public boolean allowed;
    
    /** 是否为一次性授权 Whether one-time authorization */
    public boolean onetime;

    /**
     * 构造客户端记录
     * Construct client record
     * 
     * @param uid 客户端UID
     * @param pid 客户端PID
     * @param client 客户端回调接口
     * @param packageName 客户端包名
     * @param apiVersion 客户端API版本
     */
    public ClientRecord(int uid, int pid, IStellarApplication client, String packageName, int apiVersion) {
        this.uid = uid;
        this.pid = pid;
        this.client = client;
        this.packageName = packageName;
        this.allowed = false;
        this.onetime = false;
        this.apiVersion = apiVersion;
    }

    /**
     * 分发权限请求结果到客户端
     * Dispatch permission request result to client
     * 
     * @param requestCode 请求码
     * @param allowed 是否允许
     */
    public void dispatchRequestPermissionResult(int requestCode, boolean allowed) {
        Bundle reply = new Bundle();
        reply.putBoolean(REQUEST_PERMISSION_REPLY_ALLOWED, allowed);
        try {
            client.dispatchRequestPermissionResult(requestCode, reply);
        } catch (Throwable e) {
            LOGGER.w(e, "dispatchRequestPermissionResult failed for client (uid=%d, pid=%d, package=%s)", uid, pid, packageName);
        }
    }
}

