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

package tink.org.apache.http.impl.io;

import java.io.IOException;

import tink.org.apache.http.ConnectionClosedException;
import tink.org.apache.http.HttpException;
import tink.org.apache.http.HttpMessage;
import tink.org.apache.http.HttpRequestFactory;
import tink.org.apache.http.ParseException;
import tink.org.apache.http.RequestLine;
import tink.org.apache.http.io.SessionInputBuffer;
import tink.org.apache.http.message.LineParser;
import tink.org.apache.http.message.ParserCursor;
import tink.org.apache.http.params.HttpParams;
import tink.org.apache.http.util.Args;
import tink.org.apache.http.util.CharArrayBuffer;import tink.org.apache.http.ConnectionClosedException;import tink.org.apache.http.HttpException;import tink.org.apache.http.HttpMessage;import tink.org.apache.http.HttpRequest;import tink.org.apache.http.HttpRequestFactory;import tink.org.apache.http.ParseException;import tink.org.apache.http.RequestLine;import tink.org.apache.http.io.SessionInputBuffer;import tink.org.apache.http.message.LineParser;import tink.org.apache.http.message.ParserCursor;import tink.org.apache.http.params.CoreConnectionPNames;import tink.org.apache.http.params.HttpParams;import tink.org.apache.http.util.Args;import tink.org.apache.http.util.CharArrayBuffer;

/**
 * HTTP request parser that obtain its input from an instance
 * of {@link SessionInputBuffer}.
 * <p>
 * The following parameters can be used to customize the behavior of this
 * class:
 * <ul>
 *  <li>{@link CoreConnectionPNames#MAX_HEADER_COUNT}</li>
 *  <li>{@link CoreConnectionPNames#MAX_LINE_LENGTH}</li>
 * </ul>
 *
 * @since 4.0
 *
 * @deprecated (4.2) use {@link DefaultHttpRequestParser}
 */
@Deprecated
public class HttpRequestParser extends AbstractMessageParser<HttpMessage> {

    private final HttpRequestFactory requestFactory;
    private final CharArrayBuffer lineBuf;

    /**
     * Creates an instance of this class.
     *
     * @param buffer the session input buffer.
     * @param parser the line parser.
     * @param requestFactory the factory to use to create
     *    {@link HttpRequest}s.
     * @param params HTTP parameters.
     */
    public HttpRequestParser(
            final SessionInputBuffer buffer,
            final LineParser parser,
            final HttpRequestFactory requestFactory,
            final HttpParams params) {
        super(buffer, parser, params);
        this.requestFactory = Args.notNull(requestFactory, "Request factory");
        this.lineBuf = new CharArrayBuffer(128);
    }

    @Override
    protected HttpMessage parseHead(
            final SessionInputBuffer sessionBuffer)
        throws IOException, HttpException, ParseException {

        this.lineBuf.clear();
        final int i = sessionBuffer.readLine(this.lineBuf);
        if (i == -1) {
            throw new ConnectionClosedException("Client closed connection");
        }
        final ParserCursor cursor = new ParserCursor(0, this.lineBuf.length());
        final RequestLine requestline = this.lineParser.parseRequestLine(this.lineBuf, cursor);
        return this.requestFactory.newHttpRequest(requestline);
    }

}
