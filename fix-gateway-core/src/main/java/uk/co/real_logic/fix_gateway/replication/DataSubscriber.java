/*
 * Copyright 2015 Real Logic Ltd.
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
package uk.co.real_logic.fix_gateway.replication;

import uk.co.real_logic.aeron.logbuffer.FragmentHandler;
import uk.co.real_logic.aeron.logbuffer.Header;
import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.fix_gateway.DebugLogger;
import uk.co.real_logic.fix_gateway.messages.*;
import uk.co.real_logic.fix_gateway.session.SessionHandler;

import static uk.co.real_logic.fix_gateway.replication.GatewayPublication.FRAME_SIZE;

public class DataSubscriber implements FragmentHandler
{
    public static final int UNKNOWN_TEMPLATE = -1;
    private final MessageHeaderDecoder messageHeader = new MessageHeaderDecoder();
    private final LogonDecoder logon = new LogonDecoder();
    private final ConnectDecoder connect = new ConnectDecoder();
    private final DisconnectDecoder disconnect = new DisconnectDecoder();
    private final FixMessageDecoder messageFrame = new FixMessageDecoder();
    private final SessionHandler sessionHandler;

    public DataSubscriber(final SessionHandler sessionHandler)
    {
        this.sessionHandler = sessionHandler;
    }

    public void onFragment(final DirectBuffer buffer, int offset, final int length, final Header header)
    {
        readFragment(buffer, offset);
    }

    public int readFragment(final DirectBuffer buffer, int offset)
    {
        messageHeader.wrap(buffer, offset);

        final int blockLength = messageHeader.blockLength();
        final int version = messageHeader.version();
        offset += messageHeader.size();

        switch (messageHeader.templateId())
        {
            case FixMessageDecoder.TEMPLATE_ID:
            {
                messageFrame.wrap(buffer, offset, blockLength, version);
                final int messageLength = messageFrame.bodyLength();
                sessionHandler.onMessage(
                    buffer,
                    offset + FRAME_SIZE,
                    messageLength,
                    messageFrame.connection(),
                    messageFrame.session(),
                    messageFrame.messageType());

                return offset + FRAME_SIZE + messageLength;
            }

            case DisconnectDecoder.TEMPLATE_ID:
            {
                disconnect.wrap(buffer, offset, blockLength, version);
                final long connectionId = disconnect.connection();
                DebugLogger.log("FixSubscription Disconnect: %d\n", connectionId);
                sessionHandler.onDisconnect(connectionId);
                return offset + DisconnectDecoder.BLOCK_LENGTH;
            }

            case LogonDecoder.TEMPLATE_ID:
            {
                logon.wrap(buffer, offset, blockLength, version);
                sessionHandler.onLogon(logon.connection(), logon.session());
                return logon.limit();
            }

            case ConnectDecoder.TEMPLATE_ID:
            {
                connect.wrap(buffer, offset, blockLength, version);
                final int addressOffset = offset + ConnectDecoder.BLOCK_LENGTH + ConnectDecoder.addressHeaderSize();
                sessionHandler.onConnect(connect.connection(), buffer, addressOffset, connect.addressLength());
                return connect.limit();
            }
        }

        return UNKNOWN_TEMPLATE;
    }
}