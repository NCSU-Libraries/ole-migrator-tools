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
package org.kuali.ole.contrib.persistor.common;

import groovy.transform.CompileStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * General purpose cache for lists of maps.
 * <p>
 *     Wraps a concurrent BlockingQueue to provide for the threadsafe collection of records before they are submitted,
 *     and provides other methods to simplify efficient and transparent batch JDBC or HTTP-based operations ("commit every X records").
 * </p>
 * <p>
 *     Caches can be created as <em>bounded</em> or unbounded.  An unbounded cache can grow until
 *     the client class decides to drain it (or the machine runs out of memory).
 *     A bounded cache uses a data structure with a capacity of
 *     1.2x the <em>commit interval</em> (to provide a little wiggle room, because the cache will
 *     block once it is actually full).  In either case, objects can be added into the queue and the size of the queue
 *     can be chekced against the <code>commitInterval</code> using the <code>isAtCapacity</code> method.
 * </p>
 * <p>
 *     This class is primarily of interest only if you are implemnting a Persistor strategy.
 * </p>
 * @see BlockingQueue
 */
@CompileStatic
public class RecordCache {

    public final static int DEFAULT_INTERVAL = 500;

    private final int commitInterval;

    private final BlockingQueue<Map<String,?>> _cache;

    /**
     * Creates an unbounded cache with the default commit interval
     */
    public RecordCache() {
        this(DEFAULT_INTERVAL, false);
    }

    /**
     * Creates an unbounded cache with the specified commit interval.
     * @param commitInterval
     */
    public RecordCache(int commitInterval) {
        this(commitInterval,false);
    }

    /**
     * Creates a cache with a specified commit interval that is optionally unbounded.
     * @param commitInterval the size of the cache.
     * @param bounded
     */
    public RecordCache(int commitInterval, boolean bounded) {
        this.commitInterval = commitInterval;
        if ( bounded ) {
            _cache = new ArrayBlockingQueue<>(((Double) Math.ceil(commitInterval * 1.2)).intValue());
        } else {
            _cache = new LinkedBlockingQueue<>();
        }
    }

    /**
     * Drains the current cache and returns its contents as a list.
     * @return a list of records drained from the cache.
     */
    public List<Map<String,?>> readCache() {
        List<Map<String,?>> records = new ArrayList<>(_cache.size());
        synchronized(_cache) {
            _cache.drainTo(records);
            if (  _cache.size() != 0 )  {
                throw new IllegalStateException("Cache was not drained");
            }
        }
        return records;
    }

    /**
     * Optionally inserts a record into the queue if possible, without blocking.
     * @param record
     * @return <code>true</code> if the object has been inserted into the underlying queue,
     * <code>false</code> otherwise.
     * @see BlockingQueue#offer
     */
    public boolean offer(Map<String,?> record) {
        return _cache.offer(record);
    }

    /**
     * Adds a record into the queue, blocking if it's reached capacity.
     * @param record
     * @return
     * @see BlockingQueue#add
     *
     */
    public boolean add(Map<String,?> record) {
        return _cache.add(record);
    }

    /**
     * Alias for add() to support standard Groovy syntax.
     * @param record
     * @return
     */
    public boolean leftShift(Map<String,?> record) {
        return add(record);
    }

    /**
     * Gets the current size of the cache.
     * @return
     */
    public int getSize() {
        return _cache.size();
    }

    /**
     * Checks to see whether the cache is full or overfull.
     * @return
     */
    public boolean isAtCapacity() {
        return _cache.size() >= commitInterval;
    }
}
