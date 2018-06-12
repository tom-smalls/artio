/*
 * Copyright 2015-2017 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.artio.library;

import uk.co.real_logic.artio.FixGatewayException;
import uk.co.real_logic.artio.Reply;
import uk.co.real_logic.artio.messages.GatewayError;

/**
 * Represents a reply from an asynchronous method. Methods can complete successfully, in error
 * or they can timeout.
 *
 * This class isn't threadsafe and should be used on the same thread as the FixLibrary instance.
 *
 * @param <T> the return type of the method in question.
 */
abstract class LibraryReply<T> implements Reply<T>
{
    private final long latestReplyArrivalTimeInMs;
    final LibraryPoller libraryPoller;

    long correlationId;
    private Exception error;
    private T result;
    private State state = State.EXECUTING;

    LibraryReply(final LibraryPoller libraryPoller, final long latestReplyArrivalTimeInMs)
    {
        this.libraryPoller = libraryPoller;
        this.latestReplyArrivalTimeInMs = latestReplyArrivalTimeInMs;

        if (libraryPoller.isConnected())
        {
            register();
        }
        else
        {
            onError(new FixGatewayException("Not connected to the Gateway"));
        }
    }

    protected void register()
    {
        correlationId = libraryPoller.register(this);
    }

    public Exception error()
    {
        return error;
    }

    public T resultIfPresent()
    {
        return result;
    }

    public State state()
    {
        return state;
    }

    void onComplete(final T result)
    {
        this.result = result;
        state = State.COMPLETED;
    }

    void onError(final Exception error)
    {
        this.error = error;
        state = State.ERRORED;
    }

    abstract void onError(GatewayError errorType, String errorMessage);

    /**
     * Poll the reply's duty cycle.
     *
     * @param timeInMs current time in milliseconds
     *
     * @return true if this reply should be removed from the lookup map.
     */
    boolean poll(final long timeInMs)
    {
        if (timeInMs >= latestReplyArrivalTimeInMs)
        {
            state = State.TIMED_OUT;
            return true;
        }

        if (!isExecuting())
        {
            return false;
        }

        return false;
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + "{" +
            "latestReplyArrivalTimeInMs=" + latestReplyArrivalTimeInMs +
            ", error=" + error +
            ", result=" + result +
            ", state=" + state +
            '}';
    }
}
