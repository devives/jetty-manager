/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.devives.jetty.container.contract;

/**
 * The lifecycle interface for generic components.
 * <br>
 * Classes implementing this interface have a defined life cycle
 * defined by the methods of this interface.
 */
@Contract(className = "org.eclipse.jetty.util.component.LifeCycle")
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

}
