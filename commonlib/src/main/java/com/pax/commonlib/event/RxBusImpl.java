/*
 * ===========================================================================================
 * = COPYRIGHT
 *          PAX Computer Technology(Shenzhen) CO., LTD PROPRIETARY INFORMATION
 *   This software is supplied under the terms of a license agreement or nondisclosure
 *   agreement with PAX Computer Technology(Shenzhen) CO., LTD and may not be copied or
 *   disclosed except in accordance with the terms in that agreement.
 *     Copyright (C) 2021-? PAX Computer Technology(Shenzhen) CO., LTD All rights reserved.
 * Description: // Detail description about the function of this module,
 *             // interfaces with the other modules, and dependencies.
 * Revision History:
 * Date                           Author                      Action
 * 2021/06/08                     YeHongbo                    Create
 * ===========================================================================================
 */

package com.pax.commonlib.event;

import android.os.Looper;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;
import com.pax.commonlib.application.BaseApplication;
import com.pax.commonlib.utils.LogUtils;
import com.trello.lifecycle4.android.lifecycle.AndroidLifecycle;
import com.trello.rxlifecycle4.LifecycleProvider;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableSource;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Function;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.ReplaySubject;
import io.reactivex.rxjava3.subjects.Subject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Event bus framework based on RxJava
 */
public class RxBusImpl implements IEvent {
    private static final String TAG = "RxBusImpl";

    private final Map<String, CompositeDisposable> hotDisposableMap = new ConcurrentHashMap<>();
    private final Map<String, CompositeDisposable> coldDisposableMap = new ConcurrentHashMap<>();
    private final Map<String, Subject<Boolean>> hotBusSwitcherMap = new ConcurrentHashMap<>();
    private final Map<String, Subject<Boolean>> coldBusSwitcherMap = new ConcurrentHashMap<>();
    private final Map<String, Subject<Object>> hotBusMap = new ConcurrentHashMap<>();
    private final Map<String, Subject<Object>> coldBusMap = new ConcurrentHashMap<>();

    private static final ThreadMode DEFAULT_THREAD = ThreadMode.DEFAULT;

    @Override
    public void post(Object msg) {
        if (msg == null) {
            throw new NullPointerException();
        }

        String key = msg.getClass().getName();
        Subject<Object> bus = register(key);
        if (bus != null) {
            LogUtils.d(TAG, "post: " + System.currentTimeMillis());
            bus.onNext(msg);
        }
    }

    @Override
    public void postSticky(Object msg) {
        if (msg == null) {
            throw new NullPointerException();
        }

        String key = msg.getClass().getName();
        Subject<Object> bus = registerSticky(key);
        if (bus != null) {
            LogUtils.d(TAG, "post: " + System.currentTimeMillis());
            bus.onNext(msg);
        }
    }

    private Subject<Object> register(String key) {
        Subject<Object> bus;
        if (!hotBusMap.containsKey(key) || !hotDisposableMap.containsKey(key)) {
            bus = PublishSubject.create().toSerialized();
            hotBusMap.put(key, bus);
            hotDisposableMap.put(key, new CompositeDisposable());
            hotBusSwitcherMap.put(key, BehaviorSubject.createDefault(true).toSerialized());
        } else {
            bus = hotBusMap.get(key);
        }
        return bus;
    }

    private Subject<Object> registerSticky(String key) {
        Subject<Object> bus;
        if (!coldBusMap.containsKey(key) || !coldDisposableMap.containsKey(key)) {
            bus = ReplaySubject.create().toSerialized();
            coldBusMap.put(key, bus);
            coldDisposableMap.put(key, new CompositeDisposable());
            coldBusSwitcherMap.put(key, BehaviorSubject.createDefault(true).toSerialized());
        } else {
            bus = coldBusMap.get(key);
        }
        return bus;
    }

    @Override
    public void observeForever(IObserver<Object> observer) {
        observeForever(Object.class, DEFAULT_THREAD, observer);
    }

    @Override
    public <T> void observeForever(final Class<T> msgType, final IObserver<T> observer) {
        observeForever(msgType, DEFAULT_THREAD, observer);
    }

    @Override
    public <T> void observeForever(final Class<T> msgType, final ThreadMode threadMode, final IObserver<T> observer) {
        if (msgType == null || observer == null) {
            throw new NullPointerException();
        }
        String key = msgType.getName();
        Subject<Object> bus = register(key);
        CompositeDisposable compositeDisposable = hotDisposableMap.get(key);
        Subject<Boolean> switcher = hotBusSwitcherMap.get(key);
        if (bus != null && compositeDisposable != null && switcher != null) {
            Disposable disposable = switcher.distinctUntilChanged().switchMap(
                    (Function<Boolean, ObservableSource<T>>) on -> {
                        if (on) {
                            return getObservable(bus, msgType, threadMode);
                        } else {
                            return Observable.never();
                        }
                    })
                    .doOnError(throwable -> LogUtils.e(TAG, "doOnError()", throwable))
                    .subscribe(t -> {
                        LogUtils.d(TAG, "observer: " + System.currentTimeMillis());
                        Looper looper = Looper.myLooper();
                        if (threadMode == ThreadMode.MAIN && !Looper.getMainLooper().equals(looper)) {
                            BaseApplication.getAppContext().runOnUiThread(() -> {
                                LogUtils.d(TAG, "Switch to ui thread");
                                observer.onChanged(t);
                            });
                        } else {
                            observer.onChanged(t);
                        }
                    });
            compositeDisposable.add(disposable);
        }
    }

    @Override
    public void observeStickyForever(IObserver<Object> observer) {
        observeStickyForever(Object.class, observer);
    }

    @Override
    public <T> void observeStickyForever(Class<T> msgType, IObserver<T> observer) {
        observeStickyForever(msgType, DEFAULT_THREAD, observer);
    }

    @Override
    public <T> void observeStickyForever(Class<T> msgType, ThreadMode threadMode,
            IObserver<T> observer) {
        if (msgType == null || observer == null) {
            throw new NullPointerException();
        }
        String key = msgType.getName();
        Subject<Object> bus = registerSticky(key);
        CompositeDisposable compositeDisposable = coldDisposableMap.get(key);
        Subject<Boolean> switcher = coldBusSwitcherMap.get(key);
        if (bus != null && compositeDisposable != null && switcher != null) {
            Disposable disposable = switcher.distinctUntilChanged().switchMap(
                    (Function<Boolean, ObservableSource<T>>) on -> {
                        if (on) {
                            return getObservable(bus, msgType, threadMode);
                        } else {
                            return Observable.never();
                        }
                    })
                    .doOnError(throwable -> LogUtils.e(TAG, "doOnError()", throwable))
                    .subscribe(t -> {
                        LogUtils.d(TAG, "observer: " + System.currentTimeMillis());
                        Looper looper = Looper.myLooper();
                        if (threadMode == ThreadMode.MAIN && !Looper.getMainLooper().equals(looper)) {
                            BaseApplication.getAppContext().runOnUiThread(() -> {
                                LogUtils.d(TAG, "Switch to ui thread");
                                observer.onChanged(t);
                            });
                        } else {
                            observer.onChanged(t);
                        }
                    });
            compositeDisposable.add(disposable);
        }
    }

    @Override
    public void observe(LifecycleOwner owner, final IObserver<Object> observer) {
        observe(owner, Object.class, DEFAULT_THREAD, observer);
    }

    @Override
    public <T> void observe(LifecycleOwner owner, Class<T> msgType, final IObserver<T> observer) {
        observe(owner, msgType, DEFAULT_THREAD, observer);
    }

    @Override
    public <T> void observe(LifecycleOwner owner, final Class<T> msgType, final ThreadMode threadMode,
            final IObserver<T> observer) {
        if (msgType == null || observer == null) {
            throw new NullPointerException();
        }
        String key = msgType.getName();
        Subject<Object> bus = register(key);
        CompositeDisposable compositeDisposable = hotDisposableMap.get(key);
        Subject<Boolean> switcher = hotBusSwitcherMap.get(key);
        if (bus != null && compositeDisposable != null && switcher != null) {
            LifecycleProvider<Lifecycle.Event> provider =
                    AndroidLifecycle.createLifecycleProvider(owner);
            Disposable disposable = switcher.distinctUntilChanged().switchMap(
                    (Function<Boolean, ObservableSource<T>>) on -> {
                        if (on) {
                            return getObservable(bus, msgType, threadMode);
                        } else {
                            return Observable.never();
                        }
                    })
                    .compose(provider.bindUntilEvent(Lifecycle.Event.ON_DESTROY))
                    .doOnComplete(() -> {
                        // Lifecycle ON_DESTROY Event
                        LogUtils.d(TAG, "doOnComplete()");
                        removeObserver(msgType);
                    })
                    .doOnError(throwable -> {
                        LogUtils.e(TAG, "doOnError()", throwable);
                        removeObserver(msgType);
                    })
                    .subscribe(t -> {
                        LogUtils.d(TAG, "observer: " + System.currentTimeMillis());
                        Looper looper = Looper.myLooper();
                        if (threadMode == ThreadMode.MAIN && !Looper.getMainLooper().equals(looper)) {
                            BaseApplication.getAppContext().runOnUiThread(() -> {
                                LogUtils.d(TAG, "Switch to ui thread");
                                observer.onChanged(t);
                            });
                        } else {
                            observer.onChanged(t);
                        }
                    });
            compositeDisposable.add(disposable);
        }
    }

    @Override
    public void observeSticky(LifecycleOwner owner, IObserver<Object> observer) {
        observeSticky(owner, Object.class, observer);
    }

    @Override
    public <T> void observeSticky(LifecycleOwner owner, Class<T> msgType, IObserver<T> observer) {
        observeSticky(owner, msgType, DEFAULT_THREAD, observer);
    }

    @Override
    public <T> void observeSticky(LifecycleOwner owner, Class<T> msgType, ThreadMode threadMode,
            IObserver<T> observer) {
        if (msgType == null || observer == null) {
            throw new NullPointerException();
        }
        String key = msgType.getName();
        Subject<Object> bus = registerSticky(key);
        CompositeDisposable compositeDisposable = coldDisposableMap.get(key);
        Subject<Boolean> switcher = coldBusSwitcherMap.get(key);
        if (bus != null && compositeDisposable != null && switcher != null) {
            LifecycleProvider<Lifecycle.Event> provider =
                    AndroidLifecycle.createLifecycleProvider(owner);
            Disposable disposable = switcher.distinctUntilChanged().switchMap(
                    (Function<Boolean, ObservableSource<T>>) on -> {
                        if (on) {
                            return getObservable(bus, msgType, threadMode);
                        } else {
                            return Observable.never();
                        }
                    })
                    .compose(provider.bindUntilEvent(Lifecycle.Event.ON_DESTROY))
                    .doOnComplete(() -> {
                        // Lifecycle ON_DESTROY Event
                        LogUtils.d(TAG, "doOnComplete()");
                        removeStickyObserver(msgType);
                    })
                    .doOnError(throwable -> {
                        LogUtils.e(TAG, "doOnError()", throwable);
                        removeStickyObserver(msgType);
                    })
                    .subscribe(t -> {
                        LogUtils.d(TAG, "observer: " + System.currentTimeMillis());
                        Looper looper = Looper.myLooper();
                        if (threadMode == ThreadMode.MAIN && !Looper.getMainLooper().equals(looper)) {
                            BaseApplication.getAppContext().runOnUiThread(() -> {
                                LogUtils.d(TAG, "Switch to ui thread");
                                observer.onChanged(t);
                            });
                        } else {
                            observer.onChanged(t);
                        }
                    });
            compositeDisposable.add(disposable);
        }
    }

    /**
     * Get event bus RxJava Observable object
     *
     * @param bus Event bus, actually is Subject object
     * @param msgType Message object class type, need to be consistent with the message object
     * type published by the <code>post()</code> method
     * @param threadMode The thread that executes the <code>onChanged()</code> method in the
     * observer
     * @param <T> Message object class type
     * @return Observable object
     */
    private <T> Observable<T> getObservable(Subject<Object> bus, Class<T> msgType,
            ThreadMode threadMode) {
        Observable<T> observable = bus.ofType(msgType);
        switch (threadMode) {
            case IO:
                return observable.observeOn(Schedulers.io());
            case NEW_THREAD:
                return observable.observeOn(Schedulers.newThread());
            case DEFAULT:
            case MAIN:
                /*
                 * Using observable.observeOn(AndroidSchedulers.mainThread()) will slow down the
                 * program, and in extreme cases may even cause Application Not Responding (ANR).
                 *
                 * Therefore, DO NOT specify that Observer runs on the main thread in here. The
                 * alternative solution is to check and switch the thread in Observer.
                 */
            default:
                return observable;
        }
    }

    @Override
    public Observable<Object> getObservable() {
        Subject<Object> bus = register(Object.class.getName());
        return bus.ofType(Object.class);
    }

    @Override
    public <T> Observable<T> getObservable(Class<T> msgType) {
        Subject<Object> bus = register(msgType.getName());
        return bus.ofType(msgType);
    }

    @Override
    public void block() {
        block(Object.class);
    }

    @Override
    public <T> void block(Class<T> msgType) {
        String key = msgType.getName();
        Subject<Boolean> switcher = hotBusSwitcherMap.get(key);
        if (switcher != null) {
            switcher.onNext(false);
        }
    }

    @Override
    public <T> void block(LifecycleOwner owner, Lifecycle.Event blockEvent, Class<T> msgType) {
        owner.getLifecycle().addObserver((LifecycleEventObserver) (source, event) -> {
            if (event.equals(blockEvent)) {
                block(msgType);
            }
        });
    }

    @Override
    public void blockSticky() {
        blockSticky(Object.class);
    }

    @Override
    public <T> void blockSticky(Class<T> msgType) {
        String key = msgType.getName();
        Subject<Boolean> switcher = coldBusSwitcherMap.get(key);
        if (switcher != null) {
            switcher.onNext(false);
        }
    }

    @Override
    public <T> void blockSticky(LifecycleOwner owner, Lifecycle.Event blockEvent, Class<T> msgType) {
        owner.getLifecycle().addObserver((LifecycleEventObserver) (source, event) -> {
            if (event.equals(blockEvent)) {
                blockSticky(msgType);
            }
        });
    }

    @Override
    public void open() {
        open(Object.class);
    }

    @Override
    public <T> void open(Class<T> msgType) {
        String key = msgType.getName();
        Subject<Boolean> switcher = hotBusSwitcherMap.get(key);
        if (switcher != null) {
            switcher.onNext(true);
        }
    }

    @Override
    public <T> void open(LifecycleOwner owner, Lifecycle.Event openEvent, Class<T> msgType) {
        owner.getLifecycle().addObserver((LifecycleEventObserver) (source, event) -> {
            if (event.equals(openEvent)) {
                open(msgType);
            }
        });
    }

    @Override
    public void openSticky() {
        openSticky(Object.class);
    }

    @Override
    public <T> void openSticky(Class<T> msgType) {
        String key = msgType.getName();
        Subject<Boolean> switcher = coldBusSwitcherMap.get(key);
        if (switcher != null) {
            switcher.onNext(true);
        }
    }

    @Override
    public <T> void openSticky(LifecycleOwner owner, Lifecycle.Event openEvent, Class<T> msgType) {
        owner.getLifecycle().addObserver((LifecycleEventObserver) (source, event) -> {
            if (event.equals(openEvent)) {
                openSticky(msgType);
            }
        });
    }

    @Override
    public <T> void removeObserver(Class<T> msgType) {
        String key = msgType.getName();
        hotBusMap.remove(key);
        CompositeDisposable disposable = hotDisposableMap.get(key);
        if (disposable != null) {
            disposable.clear();
            hotDisposableMap.remove(key);
        }
        hotBusSwitcherMap.remove(key);
    }

    @Override
    public <T> void removeStickyObserver(Class<T> msgType) {
        String key = msgType.getName();
        coldBusMap.remove(key);
        CompositeDisposable disposable = coldDisposableMap.get(key);
        if (disposable != null) {
            disposable.clear();
            coldDisposableMap.remove(key);
        }
        coldBusSwitcherMap.remove(key);
    }
}
