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
package org.apache.http.examples.nio;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import org.apache.http.impl.nio.reactor.DefaultListeningIOReactor;
import org.apache.http.nio.reactor.EventMask;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.reactor.ListeningIOReactor;

public class ElementalEchoServer {

    public static void main(final String[] args) throws Exception {
        final IOEventDispatch ioEventDispatch = new DefaultIoEventDispatch();
        final ListeningIOReactor ioReactor = new DefaultListeningIOReactor();
        ioReactor.listen(new InetSocketAddress(8080));
        try {
            ioReactor.execute(ioEventDispatch);
        } catch (final InterruptedIOException ex) {
            System.err.println("Interrupted");
        } catch (final IOException e) {
            System.err.println("I/O error: " + e.getMessage());
        }
        System.out.println("Shutdown");
    }

    static class DefaultIoEventDispatch implements IOEventDispatch {

        private final ByteBuffer buffer = ByteBuffer.allocate(1024);

        public void connected(final IOSession session) {
            System.out.println("connected");
            session.setEventMask(EventMask.READ);
            session.setSocketTimeout(20000);
        }

        public void inputReady(final IOSession session) {
            System.out.println("readable");
            try {
                this.buffer.compact();
                final int bytesRead = session.channel().read(this.buffer);
                if (this.buffer.position() > 0) {
                    session.setEventMask(EventMask.READ_WRITE);
                }
                System.out.println("Bytes read: " + bytesRead);
                if (bytesRead == -1) {
                    session.close();
                }
            } catch (final IOException ex) {
                System.err.println("I/O error: " + ex.getMessage());
            }
        }

        public void outputReady(final IOSession session) {
            System.out.println("writeable");
            try {
                this.buffer.flip();
                final int bytesWritten = session.channel().write(this.buffer);
                if (!this.buffer.hasRemaining()) {
                    session.setEventMask(EventMask.READ);
                }
                System.out.println("Bytes written: " + bytesWritten);
            } catch (final IOException ex) {
                System.err.println("I/O error: " + ex.getMessage());
            }
        }

        public void timeout(final IOSession session) {
            System.out.println("timeout");
            session.close();
        }

        public void disconnected(final IOSession session) {
            System.out.println("disconnected");
        }
    }

}
