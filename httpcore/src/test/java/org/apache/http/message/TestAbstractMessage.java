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
package org.apache.http.message;

import tink.org.apache.http.Header;
import tink.org.apache.http.HttpMessage;
import tink.org.apache.http.HttpVersion;
import tink.org.apache.http.ProtocolVersion;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link AbstractHttpMessage}.
 *
 */
public class TestAbstractMessage {

    static class TestHttpMessage extends AbstractHttpMessage {

        private final ProtocolVersion ver;

        public TestHttpMessage(final ProtocolVersion ver) {
            super();
            this.ver = ver != null ? ver : HttpVersion.HTTP_1_1;
        }

        public TestHttpMessage() {
            this(HttpVersion.HTTP_1_1);
        }

        public ProtocolVersion getProtocolVersion() {
            return ver;
        }

    }

    @Test
    public void testBasicProperties() {
        final HttpMessage message = new TestHttpMessage();
        Assert.assertNotNull(message.headerIterator());
        final Header[] headers = message.getAllHeaders();
        Assert.assertNotNull(headers);
        Assert.assertEquals(0, headers.length);
    }

    @Test
    public void testBasicHeaderOps() {
        final HttpMessage message = new TestHttpMessage();
        Assert.assertFalse(message.containsHeader("whatever"));

        message.addHeader("name", "1");
        message.addHeader("name", "2");

        Header[] headers = message.getAllHeaders();
        Assert.assertNotNull(headers);
        Assert.assertEquals(2, headers.length);

        Header h = message.getFirstHeader("name");
        Assert.assertNotNull(h);
        Assert.assertEquals("1", h.getValue());

        message.setHeader("name", "3");
        h = message.getFirstHeader("name");
        Assert.assertNotNull(h);
        Assert.assertEquals("3", h.getValue());
        h = message.getLastHeader("name");
        Assert.assertNotNull(h);
        Assert.assertEquals("2", h.getValue());

        // Should have no effect
        message.addHeader(null);
        message.setHeader(null);

        headers = message.getHeaders("name");
        Assert.assertNotNull(headers);
        Assert.assertEquals(2, headers.length);
        Assert.assertEquals("3", headers[0].getValue());
        Assert.assertEquals("2", headers[1].getValue());

        message.addHeader("name", "4");

        headers[1] = new BasicHeader("name", "5");
        message.setHeaders(headers);

        headers = message.getHeaders("name");
        Assert.assertNotNull(headers);
        Assert.assertEquals(2, headers.length);
        Assert.assertEquals("3", headers[0].getValue());
        Assert.assertEquals("5", headers[1].getValue());

        message.setHeader("whatever", null);
        message.removeHeaders("name");
        message.removeHeaders(null);
        headers = message.getAllHeaders();
        Assert.assertNotNull(headers);
        Assert.assertEquals(1, headers.length);
        Assert.assertEquals(null, headers[0].getValue());

        message.removeHeader(message.getFirstHeader("whatever"));
        headers = message.getAllHeaders();
        Assert.assertNotNull(headers);
        Assert.assertEquals(0, headers.length);
    }

    @Test
    public void testInvalidInput() {
        final HttpMessage message = new TestHttpMessage();
        try {
            message.addHeader(null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
        try {
            message.setHeader(null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
    }

}

