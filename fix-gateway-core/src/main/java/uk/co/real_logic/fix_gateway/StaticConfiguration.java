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
package uk.co.real_logic.fix_gateway;

import uk.co.real_logic.agrona.IoUtil;
import uk.co.real_logic.agrona.collections.Int2ObjectHashMap;
import uk.co.real_logic.fix_gateway.flyweight_api.OrderSingleAcceptor;
import uk.co.real_logic.fix_gateway.framer.session.SenderAndTargetSessionIdStrategy;
import uk.co.real_logic.fix_gateway.framer.session.SessionIdStrategy;
import uk.co.real_logic.fix_gateway.otf_api.OtfMessageAcceptor;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.stream.IntStream;

import static java.lang.Integer.getInteger;

/**
 * Configuration that exists for the entire duration of a fix gateway
 */
// TODO: add optional comp id for gateway and validate if present
public final class StaticConfiguration
{
    public static final String DEBUG_PRINT_MESSAGES_PROPERTY = "fix.core.debug";

    private static final int DEFAULT_HEARTBEAT_INTERVAL = 10;
    private static final int DEFAULT_RECEIVER_BUFFER_SIZE = 8 * 1024;
    private static final long DEFAULT_CONNECTION_TIMEOUT = 1000;
    private static final int DEFAULT_ENCODER_BUFFER_SIZE = 8 * 1024;

    /** Property name for length of the memory mapped buffers for the counters file */
    public static final String COUNTER_BUFFERS_LENGTH_PROP_NAME = "fix.counters.length";
    /** Length of the memory mapped buffers for the counters file */
    public static final int COUNTERS_BUFFER_LENGTH_DEFAULT = 64 * 1024 * 1024;

    /** Directory of the conductor buffers */
    public static final String COUNTERS_FILE_PROP_NAME = "fix.counters.file";
    /** Default directory for conductor buffers */
    public static final String COUNTERS_FILE_PROP_DEFAULT = IoUtil.tmpDirName() + "fix" + File.separator + "counters";

    private final Int2ObjectHashMap<OtfMessageAcceptor> otfAcceptors = new Int2ObjectHashMap<>();

    private int defaultHeartbeatInterval = DEFAULT_HEARTBEAT_INTERVAL;
    private int receiverBufferSize = DEFAULT_RECEIVER_BUFFER_SIZE;
    private long connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;
    private int encoderBufferSize = DEFAULT_ENCODER_BUFFER_SIZE;
    private SessionIdStrategy sessionIdStrategy = new SenderAndTargetSessionIdStrategy();

    private String host;
    private int port;
    private OtfMessageAcceptor fallbackAcceptor;
    private boolean debugPrintMessages = Boolean.getBoolean(DEBUG_PRINT_MESSAGES_PROPERTY);
    private int counterBuffersLength = getInteger(COUNTER_BUFFERS_LENGTH_PROP_NAME, COUNTERS_BUFFER_LENGTH_DEFAULT);
    private String counterBuffersFile = System.getProperty(COUNTERS_FILE_PROP_NAME, COUNTERS_FILE_PROP_DEFAULT);

    public void registerAcceptor(final OrderSingleAcceptor orderSingleAcceptor, final ErrorAcceptor errorAcceptor)
    {
    }

    public void registerAcceptor(final uk.co.real_logic.fix_gateway.reactive_api.OrderSingleAcceptor orderSingleAcceptor)
    {
    }

    public StaticConfiguration registerAcceptor(
        final OtfMessageAcceptor messageAcceptor, int firstTag, final int... tags)
    {
        otfAcceptors.put(firstTag, messageAcceptor);
        IntStream.of(tags).forEach(tag -> otfAcceptors.put(tag, messageAcceptor));
        return this;
    }

    public StaticConfiguration registerFallbackAcceptor(
            final OtfMessageAcceptor fallbackAcceptor)
    {
        this.fallbackAcceptor = fallbackAcceptor;
        return this;
    }

    public StaticConfiguration bind(final String host, final int port)
    {
        this.host = host;
        this.port = port;
        return this;
    }

    /**
     * The default interval for heartbeats if not exchanged upon logon. Specified in seconds.
     *
     * @return this
     */
    public StaticConfiguration defaultHeartbeatInterval(final int value)
    {
        defaultHeartbeatInterval = value;
        return this;
    }

    public StaticConfiguration receiverBufferSize(final int value)
    {
        receiverBufferSize = value;
        return this;
    }

    public StaticConfiguration sessionIdStrategy(final SessionIdStrategy sessionIdStrategy)
    {
        this.sessionIdStrategy = sessionIdStrategy;
        return this;
    }

    public StaticConfiguration debugPrintMessages(final boolean debugPrintMessages)
    {
        this.debugPrintMessages = debugPrintMessages;
        return this;
    }

    public StaticConfiguration counterBuffersLength(final Integer counterBuffersLength)
    {
        this.counterBuffersLength = counterBuffersLength;
        return this;
    }

    int defaultHeartbeatInterval()
    {
        return defaultHeartbeatInterval;
    }

    SessionIdStrategy sessionIdStrategy()
    {
        return sessionIdStrategy;
    }

    int receiverBufferSize()
    {
        return receiverBufferSize;
    }

    InetSocketAddress bindAddress()
    {
        return new InetSocketAddress(host, port);
    }

    long connectionTimeout()
    {
        return connectionTimeout;
    }

    int encoderBufferSize()
    {
        return encoderBufferSize;
    }

    boolean debugPrintMessages()
    {
        return debugPrintMessages;
    }

    int counterBuffersLength()
    {
        return counterBuffersLength;
    }

    String counterBuffersFile()
    {
        return counterBuffersFile;
    }

    StaticConfiguration conclude()
    {
        return this;
    }

}
