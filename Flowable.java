package leicher.textswitcher;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.MessageQueue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by leicher on 2018/5/17.
 * 事件流
 */

public class Flowable<T> extends Thread implements MessageQueue.IdleHandler{

    private static final int WHAT_CODE = 1 << 3;


    private Looper mLooper;
    private Handler mHandler;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final List<Worker> mWorkerStack = new ArrayList<>();
    private boolean mCancel;
    private final AtomicBoolean mRunning = new AtomicBoolean(false);
    private final AtomicBoolean mBeginning = new AtomicBoolean(false);
    private final AtomicBoolean mStart = new AtomicBoolean(false);
    private T mData;

    public Flowable() {
        start();
    }

    @Override
    public void run() {
        Looper.prepare();
        // 消息 队列
        MessageQueue queue = Looper.myQueue();

        mLooper = Looper.myLooper();
        mHandler = new Handler(mLooper);
        queue.addIdleHandler(this);
        synchronized (mStart){
            mStart.set(true);
            if (mBeginning.get()){
                next();
            }
            Looper.loop();
        }
    }

    @Override
    public boolean queueIdle() {
        next();
        return true;
    }


    /**
     * 下一个 消息
     */
    private void next(){
        synchronized (this){
            Worker worker = pop();
            if (worker != null){
                Message msg;
                if (worker.mMainThread){
                    msg = Message.obtain(mMainHandler, worker);
                    msg.what = WHAT_CODE;
                    mMainHandler.sendMessageDelayed(msg, worker.mDelay);
                }else {
                    msg = Message.obtain(mHandler, worker);
                    msg.what = WHAT_CODE;
                    mHandler.sendMessageDelayed(msg, worker.mDelay);
                }
            }
        }
    }

    /**
     *
     * @param run 主线程运行的下一个事件
     * @return this
     */
    public Flowable nextInMain(Runnable run){
        mWorkerStack.add(new Worker(run, true, this, 0));
        return this;
    }

    /**
     *
     * @param run 子线程运行的 下一个事件
     * @return this
     */
    public Flowable next(Runnable run){
        mWorkerStack.add(new Worker(run, false, this, 0));
        return this;
    }

    /**
     *
     * @param run 子线程运行
     * @param delay 延迟运行
     * @return this
     */
    public Flowable nextDelayed(Runnable run, long delay){
        mWorkerStack.add(new Worker(run, false, this, delay));
        return this;
    }

    /**
     *
     * @param run 主线程运行
     * @param delay 延迟
     * @return this
     */
    public Flowable nextInMainDelayed(Runnable run, long delay){
        mWorkerStack.add(new Worker(run, true, this, delay));
        return this;
    }

    /**
     * 开始事件流
     */
    public void begin(){
        if (!mCancel && !mRunning.get()){
            synchronized (mStart){
                if (mStart.get()){
                    next();
                }else {
                    mBeginning.set(true);
                }
            }
        }
    }

    public T getDate() {
        return mData;
    }


    /**
     * 事件流传递的数据 {@link Event<T>}
     * @param t 数据
     * @return this
     */
    public Flowable setData(T t) {
        this.mData = t;
        return this;
    }

    /**
     * 移除掉所有消息并 停止 loop
     */
    public void cancel(){
        mCancel = true;
        mWorkerStack.clear();
        try{
            mLooper.quit();
        }catch (Exception e){
            e.printStackTrace();
        }
    }



    /**
     *
     * @return 从栈中取下一个
     */
    private Worker pop(){
        return mWorkerStack.size() > 0 ? mWorkerStack.remove(0) : null;
    }

    private static class Worker implements Runnable{

        final Runnable mRun;
        final boolean mMainThread;
        final Flowable mFlow;
        final long mDelay;

        public Worker(Runnable mRun, boolean mMainThread, Flowable mFlow, long mDelay) {
            this.mRun = mRun;
            this.mMainThread = mMainThread;
            this.mFlow = mFlow;
            this.mDelay = mDelay;
        }

        @Override
        public void run() {
            mFlow.mRunning.compareAndSet(false, true);
            if (mRun != null){
                if (mRun instanceof Event){
                    ((Event) mRun).setT(mFlow.mData);
                }
                mRun.run();
            }
            mFlow.mRunning.compareAndSet(true, false);
            // 如果是主线程 则需要在这里发送下一个消息
            if (mMainThread){
                mFlow.next();
            }
        }
    }


    /**
     * 需要传递数据的话可以用这个
     * @param <T>
     */
    public abstract static class Event<T> implements Runnable{

        T t;

        public T getT() {
            return t;
        }

        public void setT(T t) {
            this.t = t;
        }

        @Override
        public void run() {
            onEvent(t);
        }

        public abstract void onEvent(T t);

    }

}
