/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.impl.nio;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;

import tink.org.apache.http.ConnectionClosedException;
import tink.org.apache.http.Consts;
import tink.org.apache.http.Header;
import tink.org.apache.http.HttpConnectionMetrics;
import tink.org.apache.http.HttpEntity;
import tink.org.apache.http.HttpException;
import tink.org.apache.http.HttpInetConnection;
import tink.org.apache.http.HttpMessage;
import tink.org.apache.http.HttpRequest;
import tink.org.apache.http.HttpResponse;
import tink.org.apache.http.annotation.NotThreadSafe;
import tink.org.apache.http.entity.BasicHttpEntity;
import tink.org.apache.http.entity.ContentLengthStrategy;
import tink.org.apache.http.impl.HttpConnectionMetricsImpl;
import tink.org.apache.http.impl.entity.LaxContentLengthStrategy;
import tink.org.apache.http.impl.entity.StrictContentLengthStrategy;
import tink.org.apache.http.impl.io.HttpTransportMetricsImpl;
import tink.org.apache.http.impl.nio.codecs.ChunkDecoder;
import tink.org.apache.http.impl.nio.codecs.ChunkEncoder;
import tink.org.apache.http.impl.nio.codecs.IdentityDecoder;
import tink.org.apache.http.impl.nio.codecs.IdentityEncoder;
import tink.org.apache.http.impl.nio.codecs.LengthDelimitedDecoder;
import tink.org.apache.http.impl.nio.codecs.LengthDelimitedEncoder;
import tink.org.apache.http.impl.nio.reactor.SessionInputBufferImpl;
import tink.org.apache.http.impl.nio.reactor.SessionOutputBufferImpl;
import tink.org.apache.http.io.HttpTransportMetrics;
import tink.org.apache.http.nio.ContentDecoder;
import tink.org.apache.http.nio.ContentEncoder;
import tink.org.apache.http.nio.NHttpConnection;
import tink.org.apache.http.nio.reactor.EventMask;
import tink.org.apache.http.nio.reactor.IOSession;
import tink.org.apache.http.nio.reactor.SessionBufferStatus;
import tink.org.apache.http.nio.reactor.SessionInputBuffer;
import tink.org.apache.http.nio.reactor.SessionOutputBuffer;
import tink.org.apache.http.nio.reactor.SocketAccessor;
import tink.org.apache.http.nio.util.ByteBufferAllocator;
import tink.org.apache.http.params.CoreConnectionPNames;
import tink.org.apache.http.params.CoreProtocolPNames;
import tink.org.apache.http.params.HttpParams;
import tink.org.apache.http.protocol.HTTP;
import tink.org.apache.http.protocol.HttpContext;
import tink.org.apache.http.util.Args;
import tink.org.apache.http.util.CharsetUtils;
import tink.org.apache.http.util.NetUtils;

/**
 * This class serves as a base for all {@link NHttpConnection} implementations and provides
 * functionality common to both client and server HTTP connections.
 *
 * @since 4.0
 */
@SuppressWarnings("deprecation")
@NotThreadSafe
public class NHttpConnectionBase
        implements NHttpConnection, HttpInetConnection, SessionBufferStatus, SocketAccessor {

    protected final ContentLengthStrategy incomingContentStrategy;
    protected final ContentLengthStrategy outgoingContentStrategy;

    protected final SessionInputBufferImpl inbuf;
    protected final SessionOutputBufferImpl outbuf;
    private final int fragmentSizeHint;

    protected final HttpTransportMetricsImpl inTransportMetrics;
    protected final HttpTransportMetricsImpl outTransportMetrics;
    protected final HttpConnectionMetricsImpl connMetrics;

    protected HttpContext context;
    protected IOSession session;
    protected SocketAddress remote;
    protected volatile ContentDecoder contentDecoder;
    protected volatile boolean hasBufferedInput;
    protected volatile ContentEncoder contentEncoder;
    protected volatile boolean hasBufferedOutput;
    protected volatile HttpRequest request;
    protected volatile HttpResponse response;

    protected volatile int status;

    /**
     * Creates a new instance of this class given the underlying I/O session.
     *
     * @param session the underlying I/O session.
     * @param allocator byte buffer allocator.
     * @param params HTTP parameters.
     *
     * @deprecated (4.3) use
     *   {@link NHttpConnectionBase#NHttpConnectionBase(IOSession, int, int, ByteBufferAllocator,
     *   CharsetDecoder, CharsetEncoder, ContentLengthStrategy, ContentLengthStrategy)}
     */
    @Deprecated
    public NHttpConnectionBase(
            final IOSession session,
            final ByteBufferAllocator allocator,
            final HttpParams params) {
        super();
        Args.notNull(session, "I/O session");
        Args.notNull(params, "HTTP params");

        int buffersize = params.getIntParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE, -1);
        if (buffersize <= 0) {
            buffersize = 4096;
        }
        int linebuffersize = buffersize;
        if (linebuffersize > 512) {
            linebuffersize = 512;
        }

        CharsetDecoder decoder = null;
        CharsetEncoder encoder = null;
        Charset charset = CharsetUtils.lookup(
                (String) params.getParameter(CoreProtocolPNames.HTTP_ELEMENT_CHARSET));
        if (charset != null) {
            charset = Consts.ASCII;
            decoder = charset.newDecoder();
            encoder = charset.newEncoder();
            final CodingErrorAction malformedCharAction = (CodingErrorAction) params.getParameter(
                    CoreProtocolPNames.HTTP_MALFORMED_INPUT_ACTION);
            final CodingErrorAction unmappableCharAction = (CodingErrorAction) params.getParameter(
                    CoreProtocolPNames.HTTP_UNMAPPABLE_INPUT_ACTION);
            decoder.onMalformedInput(malformedCharAction).onUnmappableCharacter(unmappableCharAction);
            encoder.onMalformedInput(malformedCharAction).onUnmappableCharacter(unmappableCharAction);
        }
        this.inbuf = new SessionInputBufferImpl(buffersize, linebuffersize, decoder, allocator);
        this.outbuf = new SessionOutputBufferImpl(buffersize, linebuffersize, encoder, allocator);
        this.fragmentSizeHint = buffersize;

        this.incomingContentStrategy = createIncomingContentStrategy();
        this.outgoingContentStrategy = createOutgoingContentStrategy();

        this.inTransportMetrics = createTransportMetrics();
        this.outTransportMetrics = createTransportMetrics();
        this.connMetrics = createConnectionMetrics(
                this.inTransportMetrics,
                this.outTransportMetrics);

        setSession(session);
        this.status = ACTIVE;
    }

    /**
     * Creates new instance NHttpConnectionBase given the underlying I/O session.
     *
     * @param session the underlying I/O session.
     * @param buffersize buffer size. Must be a positive number.
     * @param fragmentSizeHint fragment size hint.
     * @param allocator memory allocator.
     *   If <code>null</code> {@link org.apache.http.nio.util.HeapByteBufferAllocator#INSTANCE}
     *   will be used.
     * @param chardecoder decoder to be used for decoding HTTP protocol elements.
     *   If <code>null</code> simple type cast will be used for byte to char conversion.
     * @param charencoder encoder to be used for encoding HTTP protocol elements.
     *   If <code>null</code> simple type cast will be used for char to byte conversion.
     * @param incomingContentStrategy incoming content length strategy. If <code>null</code>
     *   {@link LaxContentLengthStrategy#INSTANCE} will be used.
     * @param outgoingContentStrategy outgoing content length strategy. If <code>null</code>
     *   {@link StrictContentLengthStrategy#INSTANCE} will be used.
     *
     * @since 4.3
     */
    protected NHttpConnectionBase(
            final IOSession session,
            final int buffersize,
            final int fragmentSizeHint,
            final ByteBufferAllocator allocator,
            final CharsetDecoder chardecoder,
            final CharsetEncoder charencoder,
            final ContentLengthStrategy incomingContentStrategy,
            final ContentLengthStrategy outgoingContentStrategy) {
        Args.notNull(session, "I/O session");
        Args.positive(buffersize, "Buffer size");
        int linebuffersize = buffersize;
        if (linebuffersize > 512) {
            linebuffersize = 512;
        }
        this.inbuf = new SessionInputBufferImpl(buffersize, linebuffersize, chardecoder, allocator);
        this.outbuf = new SessionOutputBufferImpl(buffersize, linebuffersize, charencoder, allocator);
        this.fragmentSizeHint = fragmentSizeHint >= 0 ? fragmentSizeHint : buffersize;

        this.inTransportMetrics = new HttpTransportMetricsImpl();
        this.outTransportMetrics = new HttpTransportMetricsImpl();
        this.connMetrics = new HttpConnectionMetricsImpl(this.inTransportMetrics, this.outTransportMetrics);
        this.incomingContentStrategy = incomingContentStrategy != null ? incomingContentStrategy :
            LaxContentLengthStrategy.INSTANCE;
        this.outgoingContentStrategy = outgoingContentStrategy != null ? outgoingContentStrategy :
            StrictContentLengthStrategy.INSTANCE;

        setSession(session);
        this.status = ACTIVE;
    }

    private void setSession(final IOSession session) {
        this.session = session;
        this.context = new SessionHttpContext(this.session);
        this.session.setBufferStatus(this);
        this.remote = this.session.getRemoteAddress();
    }

    /**
     * Binds the connection to a different {@link IOSession}. This may be necessary
     * when the underlying I/O session gets upgraded with SSL/TLS encryption.
     *
     * @since 4.2
     */
    protected void bind(final IOSession session) {
        Args.notNull(session, "I/O session");
        this.session.setBufferStatus(null);
        setSession(session);
    }

    /**
     * @since 4.2
     *
     * @deprecated (4.3) use constructor.
     */
    @Deprecated
    protected ContentLengthStrategy createIncomingContentStrategy() {
        return new LaxContentLengthStrategy();
    }

    /**
     * @since 4.2
     *
     * @deprecated (4.3) use constructor.
     */
    @Deprecated
    protected ContentLengthStrategy createOutgoingContentStrategy() {
        return new StrictContentLengthStrategy();
    }

    /**
     * @since 4.1
     *
     * @deprecated (4.3) no longer used.
     */
    @Deprecated
    protected HttpTransportMetricsImpl createTransportMetrics() {
        return new HttpTransportMetricsImpl();
    }

    /**
     * @since 4.1
     *
     * @deprecated (4.3) use decorator to add additional metrics.
     */
    @Deprecated
    protected HttpConnectionMetricsImpl createConnectionMetrics(
            final HttpTransportMetrics inTransportMetric,
            final HttpTransportMetrics outTransportMetric) {
        return new HttpConnectionMetricsImpl(inTransportMetric, outTransportMetric);
    }

    public int getStatus() {
        return this.status;
    }

    public HttpContext getContext() {
        return this.context;
    }

    public HttpRequest getHttpRequest() {
        return this.request;
    }

    public HttpResponse getHttpResponse() {
        return this.response;
    }

    public void requestInput() {
        this.session.setEvent(EventMask.READ);
    }

    public void requestOutput() {
        this.session.setEvent(EventMask.WRITE);
    }

    public void suspendInput() {
        this.session.clearEvent(EventMask.READ);
    }

    public void suspendOutput() {
        this.session.clearEvent(EventMask.WRITE);
    }

    /**
     * Initializes a specific {@link ContentDecoder} implementation based on the
     * properties of the given {@link HttpMessage} and generates an instance of
     * {@link HttpEntity} matching the properties of the content decoder.
     *
     * @param message the HTTP message.
     * @return HTTP entity.
     * @throws HttpException in case of an HTTP protocol violation.
     */
    protected HttpEntity prepareDecoder(final HttpMessage message) throws HttpException {
        final BasicHttpEntity entity = new BasicHttpEntity();
        final long len = this.incomingContentStrategy.determineLength(message);
        this.contentDecoder = createContentDecoder(
                len,
                this.session.channel(),
                this.inbuf,
                this.inTransportMetrics);
        if (len == ContentLengthStrategy.CHUNKED) {
            entity.setChunked(true);
            entity.setContentLength(-1);
        } else if (len == ContentLengthStrategy.IDENTITY) {
            entity.setChunked(false);
            entity.setContentLength(-1);
        } else {
            entity.setChunked(false);
            entity.setContentLength(len);
        }

        final Header contentTypeHeader = message.getFirstHeader(HTTP.CONTENT_TYPE);
        if (contentTypeHeader != null) {
            entity.setContentType(contentTypeHeader);
        }
        final Header contentEncodingHeader = message.getFirstHeader(HTTP.CONTENT_ENCODING);
        if (contentEncodingHeader != null) {
            entity.setContentEncoding(contentEncodingHeader);
        }
        return entity;
    }

    /**
     * Factory method for {@link ContentDecoder} instances.
     *
     * @param len content length, if known, {@link ContentLengthStrategy#CHUNKED} or
     *   {@link ContentLengthStrategy#IDENTITY}, if unknown.
     * @param channel the session channel.
     * @param buffer the session buffer.
     * @param metrics transport metrics.
     *
     * @return content decoder.
     *
     * @since 4.1
     */
    protected ContentDecoder createContentDecoder(
            final long len,
            final ReadableByteChannel channel,
            final SessionInputBuffer buffer,
            final HttpTransportMetricsImpl metrics) {
        if (len == ContentLengthStrategy.CHUNKED) {
            return new ChunkDecoder(channel, buffer, metrics);
        } else if (len == ContentLengthStrategy.IDENTITY) {
            return new IdentityDecoder(channel, buffer, metrics);
        } else {
            return new LengthDelimitedDecoder(channel, buffer, metrics, len);
        }
    }

    /**
     * Initializes a specific {@link ContentEncoder} implementation based on the
     * properties of the given {@link HttpMessage}.
     *
     * @param message the HTTP message.
     * @throws HttpException in case of an HTTP protocol violation.
     */
    protected void prepareEncoder(final HttpMessage message) throws HttpException {
        final long len = this.outgoingContentStrategy.determineLength(message);
        this.contentEncoder = createContentEncoder(
                len,
                this.session.channel(),
                this.outbuf,
                this.outTransportMetrics);
    }

    /**
     * Factory method for {@link ContentEncoder} instances.
     *
     * @param len content length, if known, {@link ContentLengthStrategy#CHUNKED} or
     *   {@link ContentLengthStrategy#IDENTITY}, if unknown.
     * @param channel the session channel.
     * @param buffer the session buffer.
     * @param metrics transport metrics.
     *
     * @return content encoder.
     *
     * @since 4.1
     */
    protected ContentEncoder createContentEncoder(
            final long len,
            final WritableByteChannel channel,
            final SessionOutputBuffer buffer,
            final HttpTransportMetricsImpl metrics) {
        if (len == ContentLengthStrategy.CHUNKED) {
            return new ChunkEncoder(channel, buffer, metrics, this.fragmentSizeHint);
        } else if (len == ContentLengthStrategy.IDENTITY) {
            return new IdentityEncoder(channel, buffer, metrics, this.fragmentSizeHint);
        } else {
            return new LengthDelimitedEncoder(channel, buffer, metrics, len, this.fragmentSizeHint);
        }
    }

    public boolean hasBufferedInput() {
        return this.hasBufferedInput;
    }

    public boolean hasBufferedOutput() {
        return this.hasBufferedOutput;
    }

    /**
     * Assets if the connection is still open.
     *
     * @throws ConnectionClosedException in case the connection has already
     *   been closed.
     */
    protected void assertNotClosed() throws ConnectionClosedException {
        if (this.status != ACTIVE) {
            throw new ConnectionClosedException("Connection is closed");
        }
    }

    public void close() throws IOException {
        if (this.status != ACTIVE) {
            return;
        }
        this.status = CLOSING;
        if (this.outbuf.hasData()) {
            this.session.setEvent(EventMask.WRITE);
        } else {
            this.session.close();
            this.status = CLOSED;
        }
    }

    public boolean isOpen() {
        return this.status == ACTIVE && !this.session.isClosed();
    }

    public boolean isStale() {
        return this.session.isClosed();
    }

    public InetAddress getLocalAddress() {
        final SocketAddress address = this.session.getLocalAddress();
        if (address instanceof InetSocketAddress) {
            return ((InetSocketAddress) address).getAddress();
        } else {
            return null;
        }
    }

    public int getLocalPort() {
        final SocketAddress address = this.session.getLocalAddress();
        if (address instanceof InetSocketAddress) {
            return ((InetSocketAddress) address).getPort();
        } else {
            return -1;
        }
    }

    public InetAddress getRemoteAddress() {
        final SocketAddress address = this.session.getRemoteAddress();
        if (address instanceof InetSocketAddress) {
            return ((InetSocketAddress) address).getAddress();
        } else {
            return null;
        }
    }

    public int getRemotePort() {
        final SocketAddress address = this.session.getRemoteAddress();
        if (address instanceof InetSocketAddress) {
            return ((InetSocketAddress) address).getPort();
        } else {
            return -1;
        }
    }

    public void setSocketTimeout(final int timeout) {
        this.session.setSocketTimeout(timeout);
    }

    public int getSocketTimeout() {
        return this.session.getSocketTimeout();
    }

    public void shutdown() throws IOException {
        this.status = CLOSED;
        this.session.shutdown();
    }

    public HttpConnectionMetrics getMetrics() {
        return this.connMetrics;
    }

    @Override
    public String toString() {
        final StringBuilder buffer = new StringBuilder();
        final SocketAddress remoteAddress = this.session.getRemoteAddress();
        final SocketAddress localAddress = this.session.getLocalAddress();
        if (remoteAddress != null && localAddress != null) {
            NetUtils.formatAddress(buffer, localAddress);
            buffer.append("<->");
            NetUtils.formatAddress(buffer, remoteAddress);
        }
        buffer.append("[");
        switch (this.status) {
        case ACTIVE:
            buffer.append("ACTIVE");
            break;
        case CLOSING:
            buffer.append("CLOSING");
            break;
        case CLOSED:
            buffer.append("CLOSED");
            break;
        }
        buffer.append("]");
        return buffer.toString();
    }

    public Socket getSocket() {
        if (this.session instanceof SocketAccessor) {
            return ((SocketAccessor) this.session).getSocket();
        } else {
            return null;
        }
    }

}
