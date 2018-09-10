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

package org.apache.http.impl.nio.reactor;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

import tink.org.apache.http.nio.reactor.IOSession;
import tink.org.apache.http.params.BasicHttpParams;
import tink.org.apache.http.params.HttpParams;

/**
* @deprecated (4.2)
*/
@Deprecated
class SSLSetupHandlerAdaptor implements org.apache.http.nio.reactor.ssl.SSLSetupHandler {

    private final SSLSetupHandler handler;

    private HttpParams params;

    public SSLSetupHandlerAdaptor(final SSLSetupHandler handler) {
        super();
        this.handler = handler;
    }

    public void initalize(final SSLEngine sslengine) throws SSLException {
        this.handler.initalize(sslengine, this.params != null ? this.params : new BasicHttpParams());
    }

    public void verify(final IOSession iosession, final SSLSession sslsession) throws SSLException {
        this.handler.verify(iosession, sslsession);
    }

    public void setParams(final HttpParams params) {
        this.params = params;
    }

}
