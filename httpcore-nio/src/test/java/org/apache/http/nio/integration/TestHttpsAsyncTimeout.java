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

package org.apache.http.nio.integration;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import se.tink.org.apache.http.HttpHost;
import se.tink.org.apache.http.HttpRequest;
import se.tink.org.apache.http.HttpResponse;
import se.tink.org.apache.http.concurrent.FutureCallback;
import se.tink.org.apache.http.impl.nio.DefaultNHttpClientConnection;
import se.tink.org.apache.http.impl.nio.DefaultNHttpServerConnection;
import se.tink.org.apache.http.impl.nio.pool.BasicNIOConnFactory;
import se.tink.org.apache.http.message.BasicHttpRequest;
import se.tink.org.apache.http.nio.NHttpClientConnection;
import se.tink.org.apache.http.nio.NHttpConnectionFactory;
import se.tink.org.apache.http.nio.pool.NIOConnFactory;
import se.tink.org.apache.http.nio.protocol.BasicAsyncRequestProducer;
import se.tink.org.apache.http.nio.protocol.BasicAsyncResponseConsumer;
import se.tink.org.apache.http.nio.protocol.HttpAsyncRequestExecutor;
import se.tink.org.apache.http.nio.testserver.HttpCoreNIOTestBase;
import se.tink.org.apache.http.nio.testserver.LoggingClientConnectionFactory;
import se.tink.org.apache.http.nio.testserver.LoggingSSLClientConnectionFactory;
import se.tink.org.apache.http.nio.testserver.LoggingServerConnectionFactory;
import se.tink.org.apache.http.nio.testserver.SSLTestContexts;
import se.tink.org.apache.http.protocol.BasicHttpContext;
import se.tink.org.apache.http.protocol.HttpContext;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestHttpsAsyncTimeout extends HttpCoreNIOTestBase {

    private ServerSocket serverSocket;

    @Before
    public void setUp() throws Exception {
        initClient();
    }

    @After
    public void tearDown() throws Exception {
        serverSocket.close();
        shutDownClient();
    }

    @Override
    protected NHttpConnectionFactory<DefaultNHttpServerConnection> createServerConnectionFactory()
        throws Exception {
        return new LoggingServerConnectionFactory();
    }

    @Override
    protected NHttpConnectionFactory<DefaultNHttpClientConnection> createClientConnectionFactory()
        throws Exception {
        return new LoggingClientConnectionFactory();
    }

    @Override
    protected NIOConnFactory<HttpHost, NHttpClientConnection> createPoolConnectionFactory()
        throws Exception {
        return new BasicNIOConnFactory(createClientConnectionFactory(),
            new LoggingSSLClientConnectionFactory(SSLTestContexts.createClientSSLContext()));
    }

    private InetSocketAddress start() throws Exception {

        final HttpAsyncRequestExecutor clientHandler = new HttpAsyncRequestExecutor();
        this.client.start(clientHandler);
        serverSocket = new ServerSocket(0);
        return new InetSocketAddress(serverSocket.getInetAddress(), serverSocket.getLocalPort());
    }

    @Test
    public void testHandshakeTimeout() throws Exception {
        // This test creates a server socket and accepts the incoming
        // socket connection without reading any data.  The client should
        // connect, be unable to progress through the handshake, and then
        // time out when SO_TIMEOUT has elapsed.

        final InetSocketAddress address = start();
        final HttpHost target = new HttpHost("localhost", address.getPort(), "https");

        final CountDownLatch latch = new CountDownLatch(1);

        final FutureCallback<HttpResponse> callback = new FutureCallback<HttpResponse>() {

            public void cancelled() {
                latch.countDown();
            }

            public void failed(final Exception ex) {
                latch.countDown();
            }

            public void completed(final HttpResponse response) {
                Assert.fail();
            }

        };

        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final HttpContext context = new BasicHttpContext();
        this.client.setTimeout(1000);
        this.client.execute(
                new BasicAsyncRequestProducer(target, request),
                new BasicAsyncResponseConsumer(),
                context, callback);
        final Socket accepted = serverSocket.accept();
        try {
            Assert.assertTrue(latch.await(10, TimeUnit.SECONDS));
        } finally {
            accepted.close();
        }
    }

}
