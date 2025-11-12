package com.stellar.server;

/**
 * Stellar应用回调接口
 * Stellar Application Callback Interface
 * 
 * 功能说明：
 * - 客户端实现此接口以接收服务端回调
 * - 用于接收服务连接确认和权限请求结果
 * - 所有方法都是oneway，不会阻塞服务端
 * 
 * 使用场景：
 * - 客户端在attachApplication时传递此接口
 * - 服务端通过此接口通知客户端各种事件
 */
interface IStellarApplication {

    /**
     * 绑定应用回调
     * Bind application callback
     * 
     * 服务端在应用连接后调用此方法，传递服务信息和权限状态
     * Called by service after application connection, passing service info and permission status
     * 
     * @param data Bundle包含：
     *             - SERVER_UID: 服务UID
     *             - SERVER_VERSION: 服务版本
     *             - SERVER_PATCH_VERSION: 服务补丁版本
     *             - SERVER_SECONTEXT: SELinux上下文
     *             - PERMISSION_GRANTED: 权限是否已授予
     *             - SHOULD_SHOW_REQUEST_PERMISSION_RATIONALE: 是否应显示权限说明
     */
    oneway void bindApplication(in Bundle data) = 1;

    /**
     * 分发权限请求结果
     * Dispatch permission request result
     * 
     * 服务端在处理权限请求后调用此方法，返回授权结果
     * Called by service after processing permission request, returning authorization result
     * 
     * @param requestCode 请求码（与requestPermission调用时的requestCode对应）
     * @param data Bundle包含：
     *             - ALLOWED: 是否允许（true/false）
     */
    oneway void dispatchRequestPermissionResult(int requestCode, in Bundle data) = 2;
}
