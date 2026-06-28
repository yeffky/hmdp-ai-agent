package com.hmdp.utils;

public interface ILock {

    /**
     * 尝试获取锁
     * @param timeoutSec 锁持有的超时时间，过期则自动释放
     * @return true代表获取锁成功，false则为失败
     */
    boolean tryLock(long timeoutSec);

    /*
    释放锁
     */
    void unlock();
}
