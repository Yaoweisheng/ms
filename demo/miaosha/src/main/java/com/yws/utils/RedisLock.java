package com.yws.utils;
//
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.data.redis.core.StringRedisTemplate;
//import org.springframework.stereotype.Component;
//
//import java.util.Collection;
//import java.util.UUID;
//import java.util.concurrent.TimeUnit;
//import java.util.concurrent.locks.AbstractQueuedSynchronizer;
//import java.util.concurrent.locks.Condition;
//import java.util.concurrent.locks.Lock;
//
//@Component
//public class RedisLock implements Lock {
//    @Autowired
//    private StringRedisTemplate stringRedisTemplate;
//
//    /** Synchronizer providing all implementation mechanics */
//    private final Sync sync;
//
//    abstract static class Sync extends AbstractQueuedSynchronizer{
//        protected Sync(StringRedisTemplate stringRedisTemplate, String lockKey) {
//            this.stringRedisTemplate = stringRedisTemplate;
//            this.lockKey = lockKey;
//        }
//
//        abstract void lock();
//
//        final String clientId = UUID.randomUUID().toString();
//        final StringRedisTemplate stringRedisTemplate;
//        final String lockKey;
//
//        final boolean nonfairTryAcquire(int acquires){
//            final Thread current = Thread.currentThread();
//            int c = getState();
//            if(c == 0){
//                if(compareAndSetState(0, acquires)){
//                    setExclusiveOwnerThread(current);
//                    return true;
//                }
//            }
//            else if(current == getExclusiveOwnerThread()){
//                int nextc = c + acquires;
//                if (nextc < 0) // overflow
//                    throw new java.lang.Error("Maximum lock count exceeded");
//                setState(nextc);
//                return true;
//            }
//            return false;
//        }
//
//        protected final boolean tryRelease(int releases){
//            int c = getState() - releases;
//            if(Thread.currentThread() != getExclusiveOwnerThread())
//                throw new IllegalMonitorStateException();
//            boolean free = false;
//            if(c == 0){
//                free = true;
//                setExclusiveOwnerThread(null);
//            }
//            setState(c);
//            if(clientId.equals(stringRedisTemplate.opsForValue().get(lockKey))){
//                stringRedisTemplate.delete(lockKey);
//            }
//            return free;
//        }
//
//        protected final boolean isHeldExclusively(){
//            return getExclusiveOwnerThread() == Thread.currentThread();
//        }
//
//        final ConditionObject newCondition(){
//            return new ConditionObject();
//        }
//
//        // Methods relayed from outer class
//
//        final Thread getOwner() {
//            return getState() == 0 ? null : getExclusiveOwnerThread();
//        }
//
//        final int getHoldCount() {
//            return isHeldExclusively() ? getState() : 0;
//        }
//
//        final boolean isLocked() {
//            return getState() != 0;
//        }
//    }
//
//    static final class NonfairSync extends Sync{
//
//        NonfairSync() {
//            super(stringRedisTemplate, lockKey);
//        }
//
//        @Override
//        void lock() {
//            if(compareAndSetState(0, 1)){
//                setExclusiveOwnerThread(Thread.currentThread());
//            } else {
//                acquire(1);
//            }
//        }
//    }
//
//    static final class FairSync extends Sync{
//
//        FairSync() {
//            super(stringRedisTemplate, lockKey);
//        }
//
//        @Override
//        void lock() {
//            acquire(1);
//        }
//
//        protected final boolean tryAcquire(int acquires){
//            final Thread current = Thread.currentThread();
//            int c = getState();
//            if(c == 0){
//                if(!hasQueuedPredecessors() && compareAndSetState(0, acquires)){
//                    setExclusiveOwnerThread(current);
//                    return tryAcquireRedisLock(TimeUnit.MILLISECONDS.toNanos(redisLockTimeout));;
//                }
//            } else if(current == getExclusiveOwnerThread()){
//                int nextc = c + acquires;
//                if (nextc < 0)
//                    throw new Error("Maximum lock count exceeded");
//                setState(nextc);
//                return true;
//            }
//            return false;
//        }
//    }
//
//    private final boolean tryAcquireRedisLock(long nanosTimeout){
//        if(nanosTimeout <= 0L){
//            return false;
//        }
//        final long deadline = System.nanoTime() + nanosTimeout;
//        int count = 0;
//        boolean interrupted = false;
//
//        try{
//            stringRedisTemplate.opsForValue().setIfAbsent(lockKey, clientId, timeout, timeUnit);
//        }
//
//    }
//
//    public RedisLock(){
//        sync = new NonfairSync();
//    }
//
//    public RedisLock(boolean fair){
//        sync = fair ? new FairSync() : new NonfairSync();
//    }
//
//    @Override
//    public void lock() {
//        sync.lock();
//    }
//
//    @Override
//    public void lockInterruptibly() throws InterruptedException {
//        sync.acquireInterruptibly(1);
//    }
//
//    @Override
//    public boolean tryLock() {
//        return sync.nonfairTryAcquire(1);
//    }
//
//    @Override
//    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
//        return sync.tryAcquireNanos(1, unit.toNanos(time));
//    }
//
//    @Override
//    public void unlock() {
//        sync.release(1);
//    }
//
//    @Override
//    public Condition newCondition() {
//        return sync.newCondition();
//    }
//
//    public int getHoldCount() {
//        return sync.getHoldCount();
//    }
//    public boolean isHeldByCurrentThread() {
//        return sync.isHeldExclusively();
//    }
//    public boolean isLocked() {
//        return sync.isLocked();
//    }
//    public final boolean isFair() {
//        return sync instanceof FairSync;
//    }
//    protected Thread getOwner() {
//        return sync.getOwner();
//    }
//    public final boolean hasQueuedThreads() {
//        return sync.hasQueuedThreads();
//    }
//    public final boolean hasQueuedThread(Thread thread) {
//        return sync.isQueued(thread);
//    }
//    public final int getQueueLength() {
//        return sync.getQueueLength();
//    }
//    protected Collection<Thread> getQueuedThreads() {
//        return sync.getQueuedThreads();
//    }
//    public boolean hasWaiters(Condition condition) {
//        if (condition == null)
//            throw new NullPointerException();
//        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
//            throw new IllegalArgumentException("not owner");
//        return sync.hasWaiters((AbstractQueuedSynchronizer.ConditionObject)condition);
//    }
//    public int getWaitQueueLength(Condition condition) {
//        if (condition == null)
//            throw new NullPointerException();
//        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
//            throw new IllegalArgumentException("not owner");
//        return sync.getWaitQueueLength((AbstractQueuedSynchronizer.ConditionObject)condition);
//    }
//    protected Collection<Thread> getWaitingThreads(Condition condition) {
//        if (condition == null)
//            throw new NullPointerException();
//        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
//            throw new IllegalArgumentException("not owner");
//        return sync.getWaitingThreads((AbstractQueuedSynchronizer.ConditionObject)condition);
//    }
//    public String toString() {
//        Thread o = sync.getOwner();
//        return super.toString() + ((o == null) ?
//                "[Unlocked]" :
//                "[Locked by thread " + o.getName() + "]");
//    }
//}
