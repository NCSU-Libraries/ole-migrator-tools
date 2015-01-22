/*
    Copyright (C) 2015 North Carolina State University

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
    */
package org.kuali.ole.contrib.persistor.common

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * (Deprecated) Wrapper around a writer that serializes write operations, making the writer safe for use
 * by multiple threads.
 * <p>
 *  It's not clear to me why this isn't actually threadsafe.  Hoping other eyes will help.
 * ,/p>
 */
@Slf4j
@CompileStatic
@Deprecated()
class ThreadSafeWriter extends Writer {

    private final BlockingQueue<WriteEvent> eventQueue = new LinkedBlockingQueue<WriteEvent>();

    private final Writer wrapped

    private final String name

    private final WriteEvent quitEvent = new WriteEvent(null, 0, 0)

    private final WriteEvent flushEvent = new WriteEvent(null, 0, 0)

    private final Thread writerThread = Thread.startDaemon(name) {
        while (!Thread.currentThread().interrupted) {
            try {
                WriteEvent evt = eventQueue.poll(200, TimeUnit.MILLISECONDS)
                if ( evt == null ) {
                    continue
                }
                if (quitEvent.is(evt)) {
                    break;
                }
                if (flushEvent.is(evt)) {
                    wrapped.flush()
                    continue
                }
                wrapped.write(evt.buf, evt.offset, evt.len)
            } catch (InterruptedException ix) {
                break
            }
        }
        log.info "writer thread ${this} shutting down"
    }

    def ThreadSafeWriter(Writer wrapped, String name = null) {
        this.wrapped = wrapped
        this.name = name ?: wrapped.toString()
    }

    public Writer leftShift(String inputString) {
        write(inputString.toCharArray(), 0, inputString.length())
    }

    @Override
    public void write(String inputString) {
        write(inputString.toCharArray(), 0, inputString.length())
    }

    @Override
    void write(char[] cbuf, int off, int len) throws IOException {
        eventQueue.add(new WriteEvent(cbuf, off, len))
    }

    @Override
    void flush() throws IOException {
        log.warn "flush() called on tsw"
        eventQueue.add(flushEvent)
    }

    @Override
    void close() throws IOException {
        writerThread.interrupt()
        eventQueue.add(quitEvent)
        writerThread.join()
        wrapped.close()
    }


    private static final class WriteEvent {
        private char[] buf;
        private int offset;
        private int len;

        WriteEvent(final char[] buf, final int offset, final int len) {
            this.buf = buf;
            this.offset = offset;
            this.len = len;
        }
    }

    private final class WriterTask implements Runnable {

        @Override
        void run() {
            while (!Thread.currentThread().interrupted) {
                try {
                    WriteEvent evt = eventQueue.poll(200, TimeUnit.MILLISECONDS)
                    if (evt == quitEvent) {
                        break;
                    }
                    if (evt == flushEvent) {
                        wrapped.flush()
                        continue
                    }
                    if (evt != null) {
                        wrapped.write(evt.buf, evt.offset, evt.len)
                    }
                } catch (InterruptedException ix) {
                    break
                }
            }

        }
    }
}
