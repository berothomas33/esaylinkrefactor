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
 * 2021/06/07                     YeHongbo                    Create
 * ===========================================================================================
 */

package com.pax.commonlib.event;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import io.reactivex.rxjava3.core.Observable;

public interface IEvent {
    /**
     * Post message to event bus.
     *
     * @param msg Message object, and the class type of message will determine which observer the
     * message is distributed to
     */
    void post(Object msg);

    /**
     * Post message to sticky event bus.
     * <br>
     * The difference with the {@code post()} method is that the message sent using this method
     * does not have to be after the observer starts to observe. If this method sends a message,
     * but the observer has not yet started observing, then the observer can receive the
     * previously sent message when it starts observing later.
     *
     * @param msg Message object, and the class type of message will determine which observer the
     * message is distributed to
     */
    void postSticky(Object msg);

    /**
     * Observe the message of Object type.
     * <br>
     * If you no longer need to observe the event, you must execute {@code removeObserver()} to
     * prevent memory leaks.
     *
     * @param observer Observer, the {@code onChanged()} method will be executed when a message
     * is received
     *
     * @see #post(Object)
     * @see #removeObserver(Class)
     */
    void observeForever(IObserver<Object> observer);

    /**
     * Observe the message of any type.
     * <br>
     * If you no longer need to observe the event, you must execute {@code removeObserver()} to
     * prevent memory leaks.
     *
     * @param msgType Message object class type, need to be consistent with the message object
     * type published by the {@code post()} method
     * @param observer Observer, the {@code onChanged()} method will be executed when a message
     * is received
     * @param <T> Message object class type
     *
     * @see #post(Object)
     * @see #removeObserver(Class)
     */
    <T> void observeForever(Class<T> msgType, IObserver<T> observer);

    /**
     * Observe the message of any type.
     * <br>
     * If you no longer need to observe the event, you must execute {@code removeObserver()} to
     * prevent memory leaks.
     *
     * @param msgType Message object class type, need to be consistent with the message object
     * type published by the {@code post()} method
     * @param threadMode The thread that executes the {@code onChanged()} method in the observer
     * @param observer Observer, the {@code onChanged()} method will be executed when a message
     * is received
     * @param <T> Message object class type
     *
     * @see #post(Object)
     * @see #removeObserver(Class)
     */
    <T> void observeForever(Class<T> msgType, ThreadMode threadMode, IObserver<T> observer);

    /**
     * Observe the sticky message of Object type.
     * <br>
     * If you no longer need to observe the event, you must execute
     * {@code removeStickyObserver()} to prevent memory leaks.
     *
     * @param observer Observer, the {@code onChanged()} method will be executed when a message
     * is received
     *
     * @see #postSticky(Object)
     * @see #removeStickyObserver(Class)
     */
    void observeStickyForever(IObserver<Object> observer);

    /**
     * Observe the sticky message of any type.
     * <br>
     * If you no longer need to observe the event, you must execute
     * {@code removeStickyObserver()} to prevent memory leaks.
     *
     * @param msgType Message object class type, need to be consistent with the message object
     * type published by the {@code postSticky()} method
     * @param observer Observer, the {@code onChanged()} method will be executed when a message
     * is received
     * @param <T> Message object class type
     *
     * @see #postSticky(Object)
     * @see #removeStickyObserver(Class)
     */
    <T> void observeStickyForever(Class<T> msgType, IObserver<T> observer);

    /**
     * Observe the sticky message of any type.
     * <br>
     * If you no longer need to observe the event, you must execute
     * {@code removeStickyObserver()} to prevent memory leaks.
     *
     * @param msgType Message object class type, need to be consistent with the message object
     * type published by the {@code postSticky()} method
     * @param threadMode The thread that executes the {@code onChanged()} method in the observer
     * @param observer Observer, the {@code onChanged()} method will be executed when a message
     * is received
     * @param <T> Message object class type
     *
     * @see #postSticky(Object)
     * @see #removeStickyObserver(Class)
     */
    <T> void observeStickyForever(Class<T> msgType, ThreadMode threadMode, IObserver<T> observer);

    /**
     * Observe the message of Object type.
     * <br>
     * This method will monitor the life cycle of LifecycleOwner. When the LifecycleOwner sends
     * out a {@code ON_DESTROY} event, the observation relationship will be automatically
     * cancelled.
     *
     * @param owner LifecycleOwner, such as Activity and Fragment. This LifecycleOwner will
     * decide whether to continue the observation relationship
     * @param observer Observer, the {@code onChanged()} method will be executed when a message
     * is received
     *
     * @see #post(Object)
     */
    void observe(LifecycleOwner owner, IObserver<Object> observer);

    /**
     * Observe the message of any type.
     * <br>
     * This method will monitor the life cycle of LifecycleOwner. When the LifecycleOwner sends
     * out a {@code ON_DESTROY} event, the observation relationship will be automatically
     * cancelled.
     *
     * @param owner LifecycleOwner, such as Activity and Fragment. This LifecycleOwner will
     * decide whether to continue the observation relationship
     * @param msgType Message object class type, need to be consistent with the message object
     * type published by the {@code post()} method
     * @param observer Observer, the {@code onChanged()} method will be executed when a message
     * is received
     * @param <T> Message object class type
     *
     * @see #post(Object)
     */
    <T> void observe(LifecycleOwner owner, Class<T> msgType, IObserver<T> observer);

    /**
     * Observe the message of any type.
     * <br>
     * This method will monitor the life cycle of LifecycleOwner. When the LifecycleOwner sends
     * out a {@code ON_DESTROY} event, the observation relationship will be automatically
     * cancelled.
     *
     * @param owner LifecycleOwner, such as Activity and Fragment. This LifecycleOwner will
     * decide whether to continue the observation relationship
     * @param msgType Message object class type, need to be consistent with the message object
     * type published by the {@code post()} method
     * @param threadMode The thread that executes the {@code onChanged()} method in the observer
     * @param observer Observer, the {@code onChanged()} method will be executed when a
     * message is received
     * @param <T> Message object class type
     *
     * @see #post(Object)
     */
    <T> void observe(LifecycleOwner owner, Class<T> msgType, ThreadMode threadMode, IObserver<T> observer);

    /**
     * Observe the sticky message of Object type.
     * <br>
     * This method will monitor the life cycle of LifecycleOwner. When the LifecycleOwner sends
     * out a {@code ON_DESTROY} event, the observation relationship will be automatically
     * cancelled.
     *
     * @param owner LifecycleOwner, such as Activity and Fragment. This LifecycleOwner will
     * decide whether to continue the observation relationship
     * @param observer Observer, the {@code onChanged()} method will be executed when a message
     * is received
     *
     * @see #postSticky(Object)
     */
    void observeSticky(LifecycleOwner owner, IObserver<Object> observer);

    /**
     * Observe the sticky message of any type.
     * <br>
     * This method will monitor the life cycle of LifecycleOwner. When the LifecycleOwner sends
     * out a {@code ON_DESTROY} event, the observation relationship will be automatically
     * cancelled.
     *
     * @param owner LifecycleOwner, such as Activity and Fragment. This LifecycleOwner will
     * decide whether to continue the observation relationship
     * @param msgType Message object class type, need to be consistent with the message object
     * type published by the {@code postSticky()} method
     * @param observer Observer, the {@code onChanged()} method will be executed when a message
     * is received
     * @param <T> Message object class type
     *
     * @see #postSticky(Object)
     */
    <T> void observeSticky(LifecycleOwner owner, Class<T> msgType, IObserver<T> observer);

    /**
     * Observe the sticky message of any type.
     * <br>
     * This method will monitor the life cycle of LifecycleOwner. When the LifecycleOwner sends
     * out a {@code ON_DESTROY} event, the observation relationship will be automatically
     * cancelled.
     *
     * @param owner LifecycleOwner, such as Activity and Fragment. This LifecycleOwner will
     * decide whether to continue the observation relationship
     * @param msgType Message object class type, need to be consistent with the message object
     * type published by the {@code postSticky()} method
     * @param threadMode The thread that executes the {@code onChanged()} method in the observer
     * @param observer Observer, the {@code onChanged()} method will be executed when a
     * message is received
     * @param <T> Message object class type
     *
     * @see #postSticky(Object)
     */
    <T> void observeSticky(LifecycleOwner owner, Class<T> msgType, ThreadMode threadMode, IObserver<T> observer);

    /**
     * Get event bus RxJava Observable object.
     * <br>
     * When the event bus post an Object class type message through {@code post()} method, this
     * Observable will emit data.
     * <br>
     * You can use this Observable to implement custom processes. After use, be sure to execute
     * {@code removeObserver()}, otherwise it will cause memory leaks.
     *
     * @return Event bus Observable object
     *
     * @see #post(Object)
     * @see #removeObserver(Class)
     */
    Observable<Object> getObservable();

    /**
     * Get event bus RxJava Observable object.
     * <br>
     * When the event bus post a specified class type message through {@code post()} method, this
     * Observable will emit data.
     * <br>
     * You can use this Observable to implement custom processes. After use, be sure to execute
     * {@code removeObserver()}, otherwise it will cause memory leaks.
     *
     * @param msgType Message object class type, need to be consistent with the message object
     * type published by the {@code post()} method
     * @param <T> Message object class type
     * @return Event bus Observable object
     *
     * @see #post(Object)
     * @see #removeObserver(Class)
     */
    <T> Observable<T> getObservable(Class<T> msgType);

    /**
     * Stop observing.
     * <br>
     * Using this method can make all observers observing Object messages stop receiving messages.
     * <br>
     * Please use {@code open()} to restart the observation. All messages sent during block and
     * open will be lost.
     * <br>
     * Please note that block is not equal to remove. Block is to temporarily stop observing.
     * After the conditions are met, you can let the observer continue observing the event.
     * However, if you execute remove, the observer disappears completely, and you can only
     * recreate the observer.
     * <br>
     * If you use the observer created by {@code observeForever()}, you still need to remove the
     * observer when you no longer observe the event, regardless of whether it is in the block
     * state.
     *
     * @see #open()
     */
    void block();

    /**
     * Stop observing.
     * <br>
     * Using this method can make all observers who observe the specified message type stop
     * receiving messages.
     * <br>
     * Please use {@code open(msgType)} or {@code open(owner, openEvent, msgType)} to restart the
     * observation. All messages sent during block and open will be lost.
     * <br>
     * Please note that block is not equal to remove. Block is to temporarily stop observing.
     * After the conditions are met, you can let the observer continue observing the event.
     * However, if you execute remove, the observer disappears completely, and you can only
     * recreate the observer.
     * <br>
     * If you use the observer created by {@code observeForever()}, you still need to remove the
     * observer when you no longer observe the event, regardless of whether it is in the block
     * state.
     *
     * @param msgType Message object class type, need to be consistent with the message object
     * type published by the {@code post()} method
     * @param <T> Message object class type
     *
     * @see #open(Class)
     * @see #open(LifecycleOwner, Lifecycle.Event, Class)
     */
    <T> void block(Class<T> msgType);

    /**
     * Stop observing.
     * <br>
     * Using this method can make all observers who observe the specified message type stop
     * receiving messages when the LifecycleOwner enters the specified state. For example, you
     * can set the current Activity to stop observing for events when it enters the onPause state.
     * <br>
     * Please use {@code open(msgType)} or {@code open(owner, openEvent, msgType)} to restart the
     * observation. All messages sent during block and open will be lost.
     * <br>
     * Please note that block is not equal to remove. Block is to temporarily stop observing.
     * After the conditions are met, you can let the observer continue observing the event.
     * However, if you execute remove, the observer disappears completely, and you can only
     * recreate the observer.
     * <br>
     * If you use the observer created by {@code observeForever()}, you still need to remove the
     * observer when you no longer observe the event, regardless of whether it is in the block
     * state.
     *
     * @param owner LifecycleOwner, such as Activity and Fragment
     * @param blockEvent Stop observing when LifecycleOwner enters this state
     * @param msgType Message object class type, need to be consistent with the message object
     * type published by the {@code post()} method
     * @param <T> Message object class type
     *
     * @see #open(Class)
     * @see #open(LifecycleOwner, Lifecycle.Event, Class)
     */
    <T> void block(LifecycleOwner owner, Lifecycle.Event blockEvent, Class<T> msgType);

    /**
     * Stop observing.
     * <br>
     * Using this method can make all observers observing sticky Object messages stop receiving
     * messages.
     * <br>
     * Please use {@code openSticky()} to restart the observation. All messages sent during block
     * and open will be temporarily saved and sent to the observer one by one according to the
     * time sequence of sending the message when open.
     * <br>
     * Please note that block is not equal to remove. Block is to temporarily stop observing.
     * After the conditions are met, you can let the observer continue observing the event.
     * However, if you execute remove, the observer disappears completely, and you can only
     * recreate the observer.
     * <br>
     * If you use the observer created by {@code observeStickyForever()}, you still need to remove
     * the observer when you no longer observe the event, regardless of whether it is in the
     * block state.
     *
     * @see #openSticky()
     */
    void blockSticky();

    /**
     * Stop observing.
     * <br>
     * Using this method can make all observers who observe the specified message type stop
     * receiving sticky messages.
     * <br>
     * Please use {@code openSticky(msgType)} or {@code openSticky(owner, openEvent, msgType)} to
     * restart the observation. All messages sent during block and open will be temporarily saved
     * and sent to the observer one by one according to the time sequence of sending the message
     * when open.
     * <br>
     * Please note that block is not equal to remove. Block is to temporarily stop observing.
     * After the conditions are met, you can let the observer continue observing the event.
     * However, if you execute remove, the observer disappears completely, and you can only
     * recreate the observer.
     * <br>
     * If you use the observer created by {@code observeStickyForever()}, you still need to remove
     * the observer when you no longer observe the event, regardless of whether it is in the
     * block state.
     *
     * @param msgType Message object class type, need to be consistent with the message object
     * type published by the {@code postSticky()} method
     * @param <T> Message object class type
     *
     * @see #openSticky(Class)
     * @see #openSticky(LifecycleOwner, Lifecycle.Event, Class)
     */
    <T> void blockSticky(Class<T> msgType);

    /**
     * Stop observing.
     * <br>
     * Using this method can make all observers who observe the specified message type stop
     * receiving sticky messages when the LifecycleOwner enters the specified state. For example,
     * you can set the current Activity to stop observing for events when it enters the onPause
     * state.
     * <br>
     * Please use {@code openSticky(msgType)} or {@code openSticky(owner, openEvent, msgType)} to
     * restart the observation. All messages sent during block and open will be temporarily saved
     * and sent to the observer one by one according to the time sequence of sending the message
     * when open.
     * <br>
     * Please note that block is not equal to remove. Block is to temporarily stop observing.
     * After the conditions are met, you can let the observer continue observing the event.
     * However, if you execute remove, the observer disappears completely, and you can only
     * recreate the observer.
     * <br>
     * If you use the observer created by {@code observeStickyForever()}, you still need to remove
     * the observer when you no longer observe the event, regardless of whether it is in the
     * block state.
     *
     * @param owner LifecycleOwner, such as Activity and Fragment
     * @param blockEvent Stop observing when LifecycleOwner enters this state
     * @param msgType Message object class type, need to be consistent with the message object
     * type published by the {@code postSticky()} method
     * @param <T> Message object class type
     *
     * @see #openSticky(Class)
     * @see #openSticky(LifecycleOwner, Lifecycle.Event, Class)
     */
    <T> void blockSticky(LifecycleOwner owner, Lifecycle.Event blockEvent, Class<T> msgType);

    /**
     * Restart observing.
     * <br>
     * Using this method can make all observers who observe Object messages restart to receiving
     * messages. All messages sent between block and open will be lost.
     *
     * @see #block()
     */
    void open();

    /**
     * Restart observing.
     * <br>
     * Using this method can make all observers who observe the specified message type restart to
     * receive messages. All messages sent between block and open will be lost.
     *
     * @param msgType Message object class type, need to be consistent with the message object
     * type published by the {@code post()} method
     * @param <T> Message object class type
     *
     * @see #block(Class)
     * @see #block(LifecycleOwner, Lifecycle.Event, Class)
     */
    <T> void open(Class<T> msgType);

    /**
     * Restart observing.
     * <br>
     * Using this method can enable all observers who observe the specified message type restart
     * to receiving messages when the LifecycleOwner enters the specified state. For example, you
     * can set the current Activity to restart observing events when it enters the onStart state.
     * All messages sent between block and open will be lost.
     *
     * @param owner LifecycleOwner, such as Activity and Fragment.
     * @param openEvent Restart observing when LifecycleOwner enters this state
     * @param msgType Message object class type, need to be consistent with the message object
     * type published by the {@code post()} method
     * @param <T> Message object class type
     *
     * @see #block(Class)
     * @see #block(LifecycleOwner, Lifecycle.Event, Class)
     */
    <T> void open(LifecycleOwner owner, Lifecycle.Event openEvent, Class<T> msgType);

    /**
     * Restart observing.
     * <br>
     * Using this method can make all observers who observe Object messages restart to receiving
     * sticky messages. All messages sent during block and open will be temporarily saved and
     * sent to the observer one by one according to the time sequence of sending the message when
     * open.
     *
     * @see #blockSticky()
     */
    void openSticky();

    /**
     * Restart observing.
     * <br>
     * Using this method can make all observers who observe the specified message type restart to
     * receiving sticky messages. All messages sent during block and open will be temporarily saved
     * and sent to the observer one by one according to the time sequence of sending the message
     * when open.
     *
     * @param msgType Message object class type, need to be consistent with the message object
     * type published by the {@code postSticky()} method
     * @param <T> Message object class type
     *
     * @see #blockSticky(Class)
     * @see #blockSticky(LifecycleOwner, Lifecycle.Event, Class)
     */
    <T> void openSticky(Class<T> msgType);

    /**
     * Restart observing.
     * <br>
     * Using this method can enable all observers who observe the specified message type
     * restart to receiving sticky messages when the LifecycleOwner enters the specified state. For
     * example, you can set the current Activity to restart observing events when it enters the
     * onStart state. All messages sent during block and open will be temporarily saved and sent
     * to the observer one by one according to the time sequence of sending the message when open.
     *
     * @param owner LifecycleOwner, such as Activity and Fragment.
     * @param openEvent Restart observing when LifecycleOwner enters this state
     * @param msgType Message object class type, need to be consistent with the message object
     * type published by the {@code postSticky()} method
     * @param <T> Message object class type
     *
     * @see #blockSticky(Class)
     * @see #blockSticky(LifecycleOwner, Lifecycle.Event, Class)
     */
    <T> void openSticky(LifecycleOwner owner, Lifecycle.Event openEvent, Class<T> msgType);

    /**
     * Remove observer.
     *
     * @param msgType Message object class type, need to be consistent with the message object
     * type published by the {@code post()} method
     * @param <T> Message object class type
     *
     * @see #observeForever(IObserver)
     * @see #observeForever(Class, IObserver)
     * @see #observeForever(Class, ThreadMode, IObserver)
     */
    <T> void removeObserver(Class<T> msgType);

    /**
     * Remove observer.
     *
     * @param msgType Message object class type, need to be consistent with the message object
     * type published by the {@code postSticky()} method
     * @param <T> Message object class type
     *
     * @see #observeStickyForever(IObserver)
     * @see #observeStickyForever(Class, IObserver)
     * @see #observeStickyForever(Class, ThreadMode, IObserver)
     */
    <T> void removeStickyObserver(Class<T> msgType);

    interface IObserver<T> {
        /**
         * Receive message
         * @param msg message
         */
        void onChanged(T msg);
    }

    enum ThreadMode {
        /**
         * The default thread mode, which thread executes the {@code post()} method, the
         * {@code onChanged()} method will executes in that thread
         */
        DEFAULT,

        /**
         * No matter which thread calls the {@code post()} method, {@code onChanged()} will
         * always be executed on the main thread.<br>
         */
        MAIN,

        /**
         * If you need to perform some time-consuming operations in the observer, it is
         * recommended to specify this thread mode.
         */
        IO,

        /**
         * Always create a new thread to observe event.
         *
         * <strong>!!!  WARNING  !!!</strong><br>
         * Creating and recycling threads may consume more resources and time. If not necessary,
         * please do not specify the thread mode.
         */
        NEW_THREAD
    }
}
