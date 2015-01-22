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
import groovyx.gpars.agent.Agent

/**
 * Writer decorator that uses a GPars Agent to make writing threadsafe.  This is  perhaps a little
 * bit less performant than the ThreadSafeWriter implementation in the same package, but it's demonstrably
 * safer.
 */
@CompileStatic
class AgentWriter extends Writer {

    private final Agent<Writer> agent;

    public AgentWriter(Writer delegate) {
        agent = new Agent(delegate)

    }

    @Override
    void write(char[] cbuf, int off, int len) throws IOException {
        agent.val.write(cbuf,off,len)
    }

    @Override
    void flush() throws IOException {
        agent.val.flush()

    }

    @Override
    void close() throws IOException {
        agent.val.close()

    }
}
