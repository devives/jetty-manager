package com.devives.jetty.contract;

import java.util.EventListener;

/**
 * The lifecycle interface for generic components.
 * <br>
 * Classes implementing this interface have a defined life cycle
 * defined by the methods of this interface.
 */
public interface LifeCycle {

    /**
     * Starts the component.
     *
     * @throws Exception If the component fails to start
     * @see #isStarted()
     * @see #stop()
     * @see #isFailed()
     */
    void start() throws Exception;

    /**
     * Stops the component.
     * The component may wait for current activities to complete
     * normally, but it can be interrupted.
     *
     * @throws Exception If the component fails to stop
     * @see #isStopped()
     * @see #start()
     * @see #isFailed()
     */
    void stop() throws Exception;

    /**
     * @return true if the component is starting.
     * @see #isStarted()
     */
    boolean isStarting();

    /**
     * @return true if the component has been started.
     * @see #start()
     * @see #isStarting()
     */
    boolean isStarted();

    /**
     * @return true if the component is starting or has been started.
     */
    boolean isRunning();

    /**
     * @return true if the component is stopping.
     * @see #isStopped()
     */
    boolean isStopping();

    /**
     * @return true if the component has been stopped.
     * @see #stop()
     * @see #isStopping()
     */
    boolean isStopped();

    /**
     * @return true if the component has failed to start or has failed to stop.
     */
    boolean isFailed();

    void addLifeCycleListener(Listener listener);

    void removeLifeCycleListener(Listener listener);

    /**
     * Listener.
     * A listener for Lifecycle events.
     */
    interface Listener extends EventListener {
        default void lifeCycleStarting(LifeCycle event) {
        }

        default void lifeCycleStarted(LifeCycle event) {
        }

        default void lifeCycleFailure(LifeCycle event, Throwable cause) {
        }

        default void lifeCycleStopping(LifeCycle event) {
        }

        default void lifeCycleStopped(LifeCycle event) {
        }
    }

}
