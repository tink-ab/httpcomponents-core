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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import se.tink.org.apache.http.Consts;
import se.tink.org.apache.http.HttpConnection;
import se.tink.org.apache.http.HttpException;
import se.tink.org.apache.http.HttpHost;
import se.tink.org.apache.http.HttpRequest;
import se.tink.org.apache.http.HttpResponse;
import se.tink.org.apache.http.concurrent.FutureCallback;
import se.tink.org.apache.http.entity.ContentType;
import se.tink.org.apache.http.entity.InputStreamEntity;
import se.tink.org.apache.http.impl.nio.DefaultNHttpClientConnection;
import se.tink.org.apache.http.impl.nio.DefaultNHttpServerConnection;
import se.tink.org.apache.http.message.BasicHttpRequest;
import se.tink.org.apache.http.nio.ContentEncoder;
import se.tink.org.apache.http.nio.IOControl;
import se.tink.org.apache.http.nio.NHttpConnectionFactory;
import se.tink.org.apache.http.nio.entity.NStringEntity;
import se.tink.org.apache.http.nio.protocol.BasicAsyncRequestConsumer;
import se.tink.org.apache.http.nio.protocol.BasicAsyncResponseProducer;
import se.tink.org.apache.http.nio.protocol.HttpAsyncExchange;
import se.tink.org.apache.http.nio.protocol.HttpAsyncExpectationVerifier;
import se.tink.org.apache.http.nio.protocol.HttpAsyncRequestConsumer;
import se.tink.org.apache.http.nio.protocol.HttpAsyncRequestHandler;
import se.tink.org.apache.http.nio.protocol.HttpAsyncRequestHandlerMapper;
import se.tink.org.apache.http.nio.protocol.UriHttpAsyncRequestHandlerMapper;
import se.tink.org.apache.http.nio.reactor.IOReactorStatus;
import se.tink.org.apache.http.nio.reactor.ListenerEndpoint;
import se.tink.org.apache.http.nio.testserver.HttpCoreNIOTestBase;
import se.tink.org.apache.http.nio.testserver.LoggingClientConnectionFactory;
import se.tink.org.apache.http.nio.testserver.LoggingServerConnectionFactory;
import se.tink.org.apache.http.protocol.BasicHttpContext;
import se.tink.org.apache.http.protocol.HttpContext;
import se.tink.org.apache.http.protocol.HttpCoreContext;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestHttpAsyncPrematureTermination extends HttpCoreNIOTestBase {

    @Before
    public void setUp() throws Exception {
        initServer();
        initClient();
    }

    @After
    public void tearDown() throws Exception {
        shutDownClient();
        shutDownServer();
    }

    @Override
    protected NHttpConnectionFactory<DefaultNHttpServerConnection> createServerConnectionFactory() throws Exception {
        return new LoggingServerConnectionFactory();
    }

    @Override
    protected NHttpConnectionFactory<DefaultNHttpClientConnection> createClientConnectionFactory() throws Exception {
        return new LoggingClientConnectionFactory();
    }

    private InetSocketAddress start(
            final HttpAsyncRequestHandlerMapper requestHandlerResolver,
            final HttpAsyncExpectationVerifier expectationVerifier) throws Exception {
        this.server.start(requestHandlerResolver, expectationVerifier);
        this.client.start();

        final ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();

        Assert.assertEquals("Test server status", IOReactorStatus.ACTIVE, this.server.getStatus());
        return (InetSocketAddress) endpoint.getAddress();
    }

    @Test
    public void testConnectionTerminatedProcessingRequest() throws Exception {
        final UriHttpAsyncRequestHandlerMapper registry = new UriHttpAsyncRequestHandlerMapper();
        registry.register("*", new HttpAsyncRequestHandler<HttpRequest>() {

            public HttpAsyncRequestConsumer<HttpRequest> processRequest(
                    final HttpRequest request,
                    final HttpContext context) throws HttpException, IOException {
                final HttpConnection conn = (HttpConnection) context.getAttribute(
                        HttpCoreContext.HTTP_CONNECTION);
                conn.shutdown();
                return new BasicAsyncRequestConsumer();
            }

            public void handle(
                    final HttpRequest request,
                    final HttpAsyncExchange httpExchange,
                    final HttpContext context) throws HttpException, IOException {
                final HttpResponse response = httpExchange.getResponse();
                response.setEntity(new NStringEntity("all is well", ContentType.TEXT_PLAIN));
                httpExchange.submitResponse();
            }

        });
        final InetSocketAddress address = start(registry, null);
        final HttpHost target = new HttpHost("localhost", address.getPort());

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
        this.client.execute(target, request, context, callback);

        Assert.assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void testConnectionTerminatedHandlingRequest() throws Exception {
        final UriHttpAsyncRequestHandlerMapper registry = new UriHttpAsyncRequestHandlerMapper();
        final CountDownLatch responseStreamClosed = new CountDownLatch(1);
        final InputStream testInputStream = new ByteArrayInputStream(
                "all is well".getBytes(Consts.ASCII.name())) {
            @Override
            public void close() throws IOException {
                responseStreamClosed.countDown();
                super.close();
            }
        };
        registry.register("*", new HttpAsyncRequestHandler<HttpRequest>() {

            public HttpAsyncRequestConsumer<HttpRequest> processRequest(
                    final HttpRequest request,
                    final HttpContext context) throws HttpException, IOException {
                return new BasicAsyncRequestConsumer();
            }

            public void handle(
                    final HttpRequest request,
                    final HttpAsyncExchange httpExchange,
                    final HttpContext context) throws HttpException, IOException {
                final HttpConnection conn = (HttpConnection) context.getAttribute(
                        HttpCoreContext.HTTP_CONNECTION);
                conn.shutdown();
                final HttpResponse response = httpExchange.getResponse();
                response.setEntity(new InputStreamEntity(testInputStream, -1));
                httpExchange.submitResponse();
            }

        });
        final InetSocketAddress address = start(registry, null);
        final HttpHost target = new HttpHost("localhost", address.getPort());

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
        this.client.execute(target, request, context, callback);

        Assert.assertTrue(latch.await(5, TimeUnit.SECONDS));
        Assert.assertTrue(responseStreamClosed.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void testConnectionTerminatedSendingResponse() throws Exception {
        final UriHttpAsyncRequestHandlerMapper registry = new UriHttpAsyncRequestHandlerMapper();
        registry.register("*", new HttpAsyncRequestHandler<HttpRequest>() {

            public HttpAsyncRequestConsumer<HttpRequest> processRequest(
                    final HttpRequest request,
                    final HttpContext context) throws HttpException, IOException {
                return new BasicAsyncRequestConsumer();
            }

            public void handle(
                    final HttpRequest request,
                    final HttpAsyncExchange httpExchange,
                    final HttpContext context) throws HttpException, IOException {
                final HttpResponse response = httpExchange.getResponse();
                response.setEntity(new NStringEntity("all is well", ContentType.TEXT_PLAIN));
                httpExchange.submitResponse(new BasicAsyncResponseProducer(response) {

                    @Override
                    public synchronized void produceContent(
                            final ContentEncoder encoder,
                            final IOControl ioctrl) throws IOException {
                        ioctrl.shutdown();
                    }

                });
            }

        });
        final InetSocketAddress address = start(registry, null);
        final HttpHost target = new HttpHost("localhost", address.getPort());

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
        this.client.execute(target, request, context, callback);

        Assert.assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

}
