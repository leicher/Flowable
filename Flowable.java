package leicher
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by leicher on 2018/5/17.
 * 事件流
 */

@SuppressWarnings("unused")
public class Flowable<T> extends Thread implements Handler.Callback, Runnable{

    private static final int WHAT_RUN = 1 << 27;
    private static final int WHAT_NEXT = 1 << 28;

    private Looper mLooper;
    private Handler mHandler;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private boolean mCancel;
    private boolean mRunning;
    private boolean mPause;
    private final List<Worker> mWorkerQueue = new ArrayList<>();
    private final AtomicBoolean mNeedBegin = new AtomicBoolean(false);
    private final AtomicBoolean mStart = new AtomicBoolean(false);
    private EventListener mListener;
    private T mData;

    public Flowable() {
        this(true);
    }

    public Flowable(boolean start){
        if (start) start();
    }

    @Override
    public void run() {
        Looper.prepare();

        mLooper = Looper.myLooper();
        mHandler = new Handler(mLooper, this);

        synchronized (mWorkerQueue){
            mStart.set(true);
            if (mNeedBegin.get()){
                mNeedBegin.set(false); // 置为初始值
                mRunning = next();
            }
        }
        Looper.loop();
        synchronized (mWorkerQueue){
            mStart.set(false);
        }
    }


    /**
     * 下一个 消息
     */
    private boolean next(){
        synchronized (mWorkerQueue){
            Worker worker = pop();
            if (worker != null){
                Message msg;
                if (worker.mMainThread){
                    msg = Message.obtain(mMainHandler, worker);
                    msg.what = WHAT_RUN;
                    mMainHandler.sendMessageDelayed(msg, worker.mDelay);
                }else {
                    msg = Message.obtain(mHandler, worker);
                    msg.what = WHAT_RUN;
                    mHandler.sendMessageDelayed(msg, worker.mDelay);
                }
                return true;
            }else { return false; }
        }
    }

    /**
     *
     * @param run 主线程运行的下一个事件
     * @return
     */
    public Flowable<T> nextInMain(Runnable run){
        checkNoNull(run);
        nextInMainDelayed(run, 0);
        return this;
    }

    /**
     *
     * @param run 子线程运行的 下一个事件
     * @return
     */
    public Flowable<T> next(Runnable run){
        checkNoNull(run);
        nextDelayed(run, 0);
        return this;
    }

    /**
     *
     * @param run 子线程运行
     * @param delay 延迟运行
     * @return
     */
    public Flowable<T> nextDelayed(Runnable run, long delay){
        checkNoNull(run);
        synchronized (mWorkerQueue) {
            mWorkerQueue.add(new Worker(run, false, this, delay));
        }
        return this;
    }

    /**
     *
     * @param run 主线程运行
     * @param delay 延迟
     * @return
     */
    public Flowable<T> nextInMainDelayed(Runnable run, long delay){
        checkNoNull(run);
        synchronized (mWorkerQueue) {
            mWorkerQueue.add(new Worker(run, true, this, delay));
        }
        return this;
    }


    /**
     * 置顶一个事件 如果队列里有了该事件 则 修改他的delay并置顶 如果没有 则加入该事件到 队列头
     * @param run
     * @param delay
     * @return
     */
    public Flowable<T> frontDelayed(Runnable run, long delay){
        frontInternal(run, delay, false);
        return this;
    }

    /**
     * 置顶 一个主线程事件
     * @param run
     * @param delay
     * @return
     */
    public Flowable<T> frontInMainDelayed(Runnable run, long delay){
        frontInternal(run, delay, true);
        return this;
    }

    public Flowable<T> frontInMain(Runnable run){
        frontInternal(run, 0, true);
        return this;
    }

    public Flowable<T> front(Runnable run){
        frontInternal(run, 0, false);
        return this;
    }

    private void frontInternal(Runnable run, long delay, boolean isMain){
        checkNoNull(run);
        synchronized (mWorkerQueue){
            Worker worker = new Worker(run, isMain, this, delay);
            int i = indexOf(worker);
            if (i >= 0){
                worker = mWorkerQueue.remove(i);
                worker.mDelay = delay;
                worker.mMainThread = isMain;
            }
            mWorkerQueue.add(0, worker);
        }
    }

    /**
     * 在 target 事件后面 紧跟着执行 run 如果事件流中没有target则忽略
     * @param run
     * @param delay
     * @param target
     * @return
     */
    public Flowable<T> behindDelayed(Runnable run, long delay, Runnable target){
        behindInternal(run, delay, false, target);
        return this;
    }

    public Flowable<T> behindInMainDelayed(Runnable run, long delay, Runnable target){
        behindInternal(run, delay, true, target);
        return this;
    }

    public Flowable<T> behind(Runnable run, Runnable target){
        behindInternal(run, 0, false, target);
        return this;
    }

    public Flowable<T> behindInMain(Runnable run, Runnable target){
        behindInternal(run, 0, true, target);
        return this;
    }


    private void behindInternal(Runnable run, long delay, boolean isMain, Runnable target){
        checkNoNull(run);
        checkNoNull(target);
        if (run != target){
            synchronized (mWorkerQueue){
                int i = indexOf(target);
                if (i >= 0){
                    Worker worker = new Worker(run, isMain, this, delay);
                    int indexOf = indexOf(worker);
                    if (indexOf >= 0){
                        i = i > indexOf ? i : ++i;
                        worker = mWorkerQueue.remove(indexOf);
                        worker.mMainThread = isMain;
                        worker.mDelay = delay;
                    }else {
                        i++;
                    }
                    mWorkerQueue.add(i, worker);
                }
            }
        }
    }

    public Flowable<T> frontOf(Runnable run, Runnable target){
        frontOfInternal(run, 0, false, target);
        return this;
    }

    public Flowable<T> frontOfInMain(Runnable run, Runnable target){
        frontOfInternal(run, 0, true, target);
        return this;
    }


    public Flowable<T> frontOfDelayed(Runnable run, long delay, Runnable target){
        frontOfInternal(run, delay, false, target);
        return this;
    }

    public Flowable<T> frontOfInMainDelayed(Runnable run, long delay, Runnable target){
        frontOfInternal(run, delay, true, target);
        return this;
    }

    private void frontOfInternal(Runnable run, long delay, boolean isMain, Runnable target){
        checkNoNull(run);
        checkNoNull(target);
        if (run != target){
            synchronized (mWorkerQueue){
                int i = indexOf(target);
                if (i > 0){ // 如果 i == 0 表示 target 正在执行便不执行此操作
                    Worker worker = new Worker(run, isMain, this, delay);
                    int indexOf = indexOf(worker);
                    if (indexOf >= 0){
                        i = i < indexOf ? i : --i;
                        worker = mWorkerQueue.remove(indexOf);
                        worker.mMainThread = isMain;
                        worker.mDelay = delay;
                    }
                    mWorkerQueue.add(i, worker);
                }
            }
        }
    }

    /**
     * 开始事件流
     */
    public Flowable<T> begin(){
        synchronized (mWorkerQueue){
            mPause = false;
            if (mCancel || mRunning) return this;
            Worker worker = pop();
            if (worker != null){
                if (mStart.get()){
                    mRunning = next();
                }else {
                    mNeedBegin.set(true);
                }
            }
        }
        return this;
    }

    public T getDate() {
        return mData;
    }


    /**
     * 事件流传递的数据 {@link Event<T>}
     * @param t 数据
     * @return
     */
    public Flowable<T> setData(T t) {
        this.mData = t;
        return this;
    }

    /**
     * 为事件流设置监听
     * @param listener
     * @return
     */
    public Flowable<T> setListener(EventListener listener){
        this.mListener = listener;
        return this;
    }

    public EventListener getListener(){
        return mListener;
    }

    public Flowable<T> pause(){
        synchronized (mWorkerQueue){
            mPause = true;
        }
        return this;
    }

    /**
     * 取消掉某一个 run
     * @param run
     */
    public Flowable<T> cancel(Runnable run){
        synchronized (mWorkerQueue){
            int i = indexOf(run);
            if (i >= 0){
                mWorkerQueue.remove(i);
            }
        }
        return this;
    }

    /**
     * 移除掉所有消息并 停止 loop
     */
    public void shutdown(){
        synchronized (mWorkerQueue){
            mWorkerQueue.clear();
            mCancel = true;
        }
        try{
            if (mLooper != null)
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
        return mWorkerQueue.size() > 0 ? mWorkerQueue.get(0) : null;
    }

    public int indexOf(Worker worker){
        return mWorkerQueue.indexOf(worker);
    }

    public int indexOf(Runnable run){
        return mWorkerQueue.indexOf(new Worker(run));
    }

    private void checkNoNull(Runnable run){
        if (run == null) throw new NullPointerException("runnable can not be null");
    }

    protected boolean dispatchEventStart(Runnable run){
        return mListener == null || mListener.onEventStart(this, run);
    }

    protected void dispatchEventEnd(Runnable run){
        if (mListener != null) mListener.onEventEnd(this, run);
    }

    protected void dispatchError(Runnable run, Throwable e){
        if (mListener != null) mListener.onEventError(this, run, e);
    }

    @Override
    public boolean handleMessage(Message msg) {
        // 在这里开始下一个任务
        switch (msg.what){
            case WHAT_NEXT:
                synchronized (mWorkerQueue){
                    mWorkerQueue.remove((Worker) msg.obj);
                    mRunning = !mPause && next();
                }
                break;
                default: break;
        }
        return true;
    }

    private static class Worker implements Runnable{

        final Runnable mRun;
        volatile boolean mMainThread;
        volatile Flowable mFlow;
        volatile long mDelay;

        Worker(Runnable mRun) {
            this.mRun = mRun;
        }

        Worker(Runnable mRun, boolean mMainThread, Flowable mFlow, long mDelay) {
            this.mRun = mRun;
            this.mMainThread = mMainThread;
            this.mFlow = mFlow;
            this.mDelay = mDelay;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void run() {
            if (mRun instanceof Event){
                ((Event) mRun).setT(mFlow.mData);
            }
            if(mFlow.dispatchEventStart(mRun)){
                try{
                    mRun.run();
                }catch (Throwable e){
                    e.printStackTrace();
                    mFlow.dispatchError(mRun, e);
                }
            }
            mFlow.dispatchEventEnd(mRun);
            Handler handler = mFlow.mHandler;
            if (handler != null){
                handler.sendMessage(handler.obtainMessage(WHAT_NEXT, this));
            }
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) return false;
            if (!(obj instanceof Worker)) return false;
            Worker w = (Worker) obj;
            if (w.mRun == this.mRun) return true;
            return super.equals(obj);
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

    /**
     * 事件队列里得每一个事件运行开始和结束 会回调相关的生命周期方法
     */
    public interface EventListener{

        /**
         * 当事件将要开始时
         * @param flow Flowable 对象
         * @param run 你的Runnable 对象
         * @return 返回 true 则 执行该runnable的run方法 否则 不执行
         */
        boolean onEventStart(Flowable flow, Runnable run);

        /**
         * 当事件结束时回调
         * @param flow
         * @param run
         */
        void onEventEnd(Flowable flow, Runnable run);

        /**
         * 当事件的 run 方法 抛出异常
         * @param flow
         * @param run
         */
        void onEventError(Flowable flow, Runnable run, Throwable e);

    }


}
