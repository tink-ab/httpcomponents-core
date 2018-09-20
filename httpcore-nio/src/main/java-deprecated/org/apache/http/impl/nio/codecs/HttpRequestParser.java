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

package org.apache.http.impl.nio.codecs;

import tink.org.apache.http.HttpException;
import tink.org.apache.http.HttpMessage;
import tink.org.apache.http.HttpRequest;import tink.org.apache.http.HttpRequestFactory;
import tink.org.apache.http.ParseException;
import tink.org.apache.http.RequestLine;
import tink.org.apache.http.message.LineParser;
import tink.org.apache.http.message.ParserCursor;
import org.apache.http.nio.reactor.SessionInputBuffer;
import tink.org.apache.http.params.CoreConnectionPNames;import tink.org.apache.http.params.HttpParams;
import tink.org.apache.http.util.Args;
import tink.org.apache.http.util.CharArrayBuffer;

/**
 * Default {@link org.apache.http.nio.NHttpMessageParser} implementation
 * for {@link HttpRequest}s.
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
 * @deprecated (4.1) use {@link DefaultHttpRequestParser}
 */
@SuppressWarnings("rawtypes")
@Deprecated
public class HttpRequestParser extends AbstractMessageParser {

    private final HttpRequestFactory requestFactory;

    public HttpRequestParser(
            final SessionInputBuffer buffer,
            final LineParser parser,
            final HttpRequestFactory requestFactory,
            final HttpParams params) {
        super(buffer, parser, params);
        Args.notNull(requestFactory, "Request factory");
        this.requestFactory = requestFactory;
    }

    @Override
    protected HttpMessage createMessage(final CharArrayBuffer buffer)
            throws HttpException, ParseException {
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        final RequestLine requestLine = lineParser.parseRequestLine(buffer, cursor);
        return this.requestFactory.newHttpRequest(requestLine);
    }

}
