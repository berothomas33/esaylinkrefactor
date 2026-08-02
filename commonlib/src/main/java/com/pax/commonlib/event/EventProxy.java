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

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import com.pax.commonlib.utils.LogUtils;
import io.reactivex.rxjava3.core.Observable;

public class EventProxy implements IEvent {
    private static final String TAG = "Event";

    private IEvent iEvent;

    private static final class LazyHolder {
        private static final EventProxy INSTANCE = new EventProxy();
    }

    public static EventProxy getInstance() {
        return LazyHolder.INSTANCE;
    }

    /**
     * Set event bus framework
     *
     * @param iEvent event bus instance
     */
    public void init(@NonNull IEvent iEvent) {
        LogUtils.d(TAG, "init: " + iEvent.getClass().getSimpleName());
        this.iEvent = iEvent;
    }

    @Override
    public void post(@NonNull Object msg) {
        LogUtils.d(TAG, "post message: " + msg.getClass().getSimpleName());
        iEvent.post(msg);
    }

    @Override
    public void postSticky(Object msg) {
        LogUtils.d(TAG, "post sticky message: " + msg.getClass().getSimpleName());
        iEvent.postSticky(msg);
    }

    @Override
    public void observeForever(@NonNull IObserver<Object> observer) {
        LogUtils.d(TAG, "observeForever: Object");
        iEvent.observeForever(observer);
    }

    @Override
    public <T> void observeForever(@NonNull Class<T> msgType, @NonNull IObserver<T> observer) {
        LogUtils.d(TAG, "observeForever: " + msgType.getSimpleName());
        iEvent.observeForever(msgType, observer);
    }

    @Override
    public <T> void observeForever(
            @NonNull Class<T> msgType,
            ThreadMode threadMode,
            @NonNull IObserver<T> observer
    ) {
        LogUtils.d(TAG, "observeForever: " + msgType.getSimpleName());
        iEvent.observeForever(msgType, threadMode, observer);
    }

    @Override
    public void observeStickyForever(@NonNull IObserver<Object> observer) {
        LogUtils.d(TAG, "observeStickyForever: Object");
        iEvent.observeStickyForever(observer);
    }

    @Override
    public <T> void observeStickyForever(@NonNull Class<T> msgType,
            @NonNull IObserver<T> observer) {
        LogUtils.d(TAG, "observeStickyForever: " + msgType.getSimpleName());
        iEvent.observeStickyForever(msgType, observer);
    }

    @Override
    public <T> void observeStickyForever(@NonNull Class<T> msgType, ThreadMode threadMode,
            @NonNull IObserver<T> observer) {
        LogUtils.d(TAG, "observeStickyForever: " + msgType.getSimpleName());
        iEvent.observeStickyForever(msgType, threadMode, observer);
    }

    @Override
    public void observe(LifecycleOwner owner, IObserver<Object> observer) {
        LogUtils.d(TAG, "observe: Object");
        iEvent.observe(owner, observer);
    }

    @Override
    public <T> void observe(LifecycleOwner owner, Class<T> msgType, IObserver<T> observer) {
        LogUtils.d(TAG, "observe: " + msgType.getSimpleName());
        iEvent.observe(owner, msgType, observer);
    }

    @Override
    public <T> void observe(LifecycleOwner owner, Class<T> msgType, ThreadMode threadMode,
            IObserver<T> observer) {
        LogUtils.d(TAG, "observe: " + msgType.getSimpleName());
        iEvent.observe(owner, msgType, threadMode, observer);
    }

    @Override
    public void observeSticky(LifecycleOwner owner, IObserver<Object> observer) {
        LogUtils.d(TAG, "observeSticky: Object");
        iEvent.observeSticky(owner, observer);
    }

    @Override
    public <T> void observeSticky(LifecycleOwner owner, Class<T> msgType, IObserver<T> observer) {
        LogUtils.d(TAG, "observeSticky: " + msgType.getSimpleName());
        iEvent.observeSticky(owner, msgType, observer);
    }

    @Override
    public <T> void observeSticky(LifecycleOwner owner, Class<T> msgType, ThreadMode threadMode,
            IObserver<T> observer) {
        LogUtils.d(TAG, "observeSticky: " + msgType.getSimpleName());
        iEvent.observeSticky(owner, msgType, threadMode, observer);
    }

    @Override
    public Observable<Object> getObservable() {
        LogUtils.d(TAG, "getObservable: Object");
        return iEvent.getObservable();
    }

    @Override
    public <T> Observable<T> getObservable(Class<T> msgType) {
        LogUtils.d(TAG, "getObservable: " + msgType.getSimpleName());
        return iEvent.getObservable(msgType);
    }

    @Override
    public void block() {
        LogUtils.d(TAG, "block");
        iEvent.block();
    }

    @Override
    public <T> void block(Class<T> msgType) {
        LogUtils.d(TAG, "block: " + msgType.getSimpleName());
        iEvent.block(msgType);
    }

    @Override
    public <T> void block(LifecycleOwner owner, Lifecycle.Event blockEvent, Class<T> msgType) {
        LogUtils.d(TAG, "block: " + msgType.getSimpleName());
        iEvent.block(owner, blockEvent, msgType);
    }

    /**
     * Automatic block observes who observing of the specified message type and opens them until a
     * certain Lifecycle event condition is met.
     *
     * It is equivalent to executing the {@code block()} and {@code open()} methods at one time.
     *
     * @param owner LifecycleOwner, such as Activity and Fragment
     * @param blockEvent Stop observing when LifecycleOwner enters this state
     * @param openEvent Restart observing when LifecycleOwner enters this state
     * @param msgType Message object class type, need to be consistent with the message object
     * type published by the {@code post()} method
     * @param <T> Message object class type
     */
    public <T> void autoBlockUntil(
            LifecycleOwner owner,
            Lifecycle.Event blockEvent,
            Lifecycle.Event openEvent,
            Class<T> msgType) {
        LogUtils.d(TAG, "auto block: " + msgType.getSimpleName());
        iEvent.block(owner, blockEvent, msgType);
        iEvent.open(owner, openEvent, msgType);
    }

    @Override
    public void blockSticky() {
        LogUtils.d(TAG, "block sticky");
        iEvent.blockSticky();
    }

    @Override
    public <T> void blockSticky(Class<T> msgType) {
        LogUtils.d(TAG, "block sticky: " + msgType.getSimpleName());
        iEvent.blockSticky(msgType);
    }

    @Override
    public <T> void blockSticky(LifecycleOwner owner, Lifecycle.Event blockEvent, Class<T> msgType) {
        LogUtils.d(TAG, "block sticky: " + msgType.getSimpleName());
        iEvent.blockSticky(owner, blockEvent, msgType);
    }

    /**
     * Automatic block observes who observing of the specified sticky message type and opens them
     * until a certain Lifecycle event condition is met.
     *
     * It is equivalent to executing the {@code blockSticky()} and {@code openSticky()} methods at
     * one time.
     *
     * @param owner LifecycleOwner, such as Activity and Fragment
     * @param blockEvent Stop observing when LifecycleOwner enters this state
     * @param openEvent Restart observing when LifecycleOwner enters this state
     * @param msgType Message object class type, need to be consistent with the message object
     * type published by the {@code postSticky()} method
     * @param <T> Message object class type
     */
    public <T> void autoBlockStickyUntil(
            LifecycleOwner owner,
            Lifecycle.Event blockEvent,
            Lifecycle.Event openEvent,
            Class<T> msgType) {
        LogUtils.d(TAG, "auto block sticky: " + msgType.getSimpleName());
        iEvent.blockSticky(owner, blockEvent, msgType);
        iEvent.openSticky(owner, openEvent, msgType);
    }

    @Override
    public void open() {
        LogUtils.d(TAG, "open");
        iEvent.open();
    }

    @Override
    public <T> void open(Class<T> msgType) {
        LogUtils.d(TAG, "open: " + msgType.getSimpleName());
        iEvent.open(msgType);
    }

    @Override
    public <T> void open(LifecycleOwner owner, Lifecycle.Event openEvent, Class<T> msgType) {
        LogUtils.d(TAG, "open: " + msgType.getSimpleName());
        iEvent.open(owner, openEvent, msgType);
    }

    @Override
    public void openSticky() {
        LogUtils.d(TAG, "open sticky");
        iEvent.openSticky();
    }

    @Override
    public <T> void openSticky(Class<T> msgType) {
        LogUtils.d(TAG, "open sticky: " + msgType.getSimpleName());
        iEvent.openSticky(msgType);
    }

    @Override
    public <T> void openSticky(LifecycleOwner owner, Lifecycle.Event openEvent, Class<T> msgType) {
        LogUtils.d(TAG, "open sticky: " + msgType.getSimpleName());
        iEvent.openSticky(owner, openEvent, msgType);
    }

    @Override
    public <T> void removeObserver(Class<T> msgType) {
        LogUtils.d(TAG, "remove observer: " + msgType.getSimpleName());
        iEvent.removeObserver(msgType);
    }

    @Override
    public <T> void removeStickyObserver(Class<T> msgType) {
        LogUtils.d(TAG, "remove sticky observer: " + msgType.getSimpleName());
        iEvent.removeStickyObserver(msgType);
    }
}
