/*
 * Copyright 2015-2016 Real Logic Ltd.
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
package uk.co.real_logic.fix_gateway.engine.logger;

import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.Agent;

import java.util.List;

/**
 * Incrementally builds indexes by polling a subscription.
 */
public class Indexer implements Agent, FragmentHandler
{
    private static final int LIMIT = 10;

    private final List<Index> indices;
    private final Subscription subscription;
    private final ArchiveReader archiveReader;

    public Indexer(
        final List<Index> indices, final Subscription subscription, final ArchiveReader archiveReader)
    {
        this.indices = indices;
        this.subscription = subscription;
        this.archiveReader = archiveReader;
        catchIndexUp();
    }

    public int doWork() throws Exception
    {
        return subscription.poll(this, LIMIT);
    }

    private void catchIndexUp()
    {
        for (final Index index : indices)
        {
            index.readLastPosition((aeronSessionId, position) ->
            {
                final ArchiveReader.SessionReader sessionReader = archiveReader.session(aeronSessionId);
                if (sessionReader != null)
                {
                    do
                    {
                        position = sessionReader.read(position, index);
                    } while (position > 0);
                }
            });
        }
    }

    public void onFragment(final DirectBuffer buffer, final int offset, final int length, final Header header)
    {
        final int streamId = header.streamId();
        final int aeronSessionId = header.sessionId();
        final long position = header.position();
        for (final Index index : indices)
        {
            index.indexRecord(buffer, offset, length, streamId, aeronSessionId, position);
        }
    }

    public void onClose()
    {
        indices.forEach(Index::close);
    }

    public String roleName()
    {
        return "Indexer";
    }
}
