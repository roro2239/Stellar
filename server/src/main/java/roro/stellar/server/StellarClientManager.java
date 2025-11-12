package roro.stellar.server;

/**
 * Stellar客户端管理器
 * Stellar Client Manager
 * 
 * <p>功能说明 Features：</p>
 * <ul>
 * <li>管理所有连接的客户端 - Manages all connected clients</li>
 * <li>继承自ClientManager，使用StellarConfigManager - Extends ClientManager with StellarConfigManager</li>
 * <li>提供客户端查找、添加、移除等功能 - Provides client find, add, remove functions</li>
 * </ul>
 */
public class StellarClientManager extends ClientManager<StellarConfigManager> {

    /**
     * 构造客户端管理器
     * Construct client manager
     * 
     * @param configManager 配置管理器
     */
    public StellarClientManager(StellarConfigManager configManager) {
        super(configManager);
    }
}



