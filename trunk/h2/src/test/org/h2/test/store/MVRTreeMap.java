/*
 * Copyright 2004-2011 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test.store;

import java.util.ArrayList;
import java.util.Iterator;
import org.h2.dev.store.btree.Cursor;
import org.h2.dev.store.btree.CursorPos;
import org.h2.dev.store.btree.DataType;
import org.h2.dev.store.btree.MVMap;
import org.h2.dev.store.btree.MVStore;
import org.h2.dev.store.btree.Page;
import org.h2.util.New;

/**
 * An r-tree implementation. It uses the quadratic split algorithm.
 *
 * @param <K> the key class
 * @param <V> the value class
 */
public class MVRTreeMap<K, V> extends MVMap<K, V> {

    final SpatialType keyType;
    private boolean quadraticSplit;

    MVRTreeMap(MVStore store, int id, String name, DataType keyType,
            DataType valueType, long createVersion) {
        super(store, id, name, keyType, valueType, createVersion);
        this.keyType = (SpatialType) keyType;
    }

    @SuppressWarnings("unchecked")
    public V get(Object key) {
        checkOpen();
        return (V) get(root, key);
    }

    /**
     * Iterate over all keys that have an intersection with the given rectangle.
     *
     * @param x the rectangle
     * @return the iterator
     */
    public Iterator<K> findIntersectingKeys(K x) {
        checkOpen();
        return new RTreeCursor<K>(this, root, x) {
            protected boolean check(K key, K test) {
                System.out.println("key " + key + " contains " + test + " = " + keyType.contains(key, test));

                return keyType.contains(key, test);
            }
        };
    }

    /**
     * Iterate over all keys that are fully contained within the given rectangle.
     *
     * @param x the rectangle
     * @return the iterator
     */
    public Iterator<K> findContainedKeys(K x) {
        checkOpen();
        return new RTreeCursor<K>(this, root, x) {
            protected boolean check(K key, K test) {
                System.out.println("key " + key + " isInside " + test + " = " + keyType.contains(test, key));
                return keyType.isInside(test, key);
            }
        };
    }

    private boolean contains(Page p, int index, Object key) {
        return keyType.contains(p.getKey(index), key);
    }

    /**
     * Get the object for the given key. An exact match is required.
     *
     * @param p the page
     * @param key the key
     * @return the value, or null if not found
     */
    protected Object get(Page p, Object key) {
        if (!p.isLeaf()) {
            for (int i = 0; i < p.getKeyCount(); i++) {
                if (contains(p, i, key)) {
                    Object o = get(p.getChildPage(i), key);
                    if (o != null) {
                        return o;
                    }
                }
            }
        } else {
            for (int i = 0; i < p.getKeyCount(); i++) {
                if (keyType.equals(p.getKey(i), key)) {
                    return p.getValue(i);
                }
            }
        }
        return null;
    }

    protected Page getPage(K key) {
        return getPage(root, key);
    }

    private Page getPage(Page p, Object key) {
        if (!p.isLeaf()) {
            for (int i = 0; i < p.getKeyCount(); i++) {
                if (contains(p, i, key)) {
                    Page x = getPage(p.getChildPage(i), key);
                    if (x != null) {
                        return x;
                    }
                }
            }
        } else {
            for (int i = 0; i < p.getKeyCount(); i++) {
                if (keyType.equals(p.getKey(i), key)) {
                    return p;
                }
            }
        }
        return null;
    }

    protected Object remove(Page p, long writeVersion, Object key) {
        Object result = null;
        if (p.isLeaf()) {
            for (int i = 0; i < p.getKeyCount(); i++) {
                if (keyType.equals(p.getKey(i), key)) {
                    result = p.getValue(i);
                    p.remove(i);
                    if (p.getKeyCount() == 0) {
                        removePage(p);
                    }
                    break;
                }
            }
            return result;
        }
        for (int i = 0; i < p.getKeyCount(); i++) {
            if (contains(p, i, key)) {
                Page cOld = p.getChildPage(i);
                Page c = cOld.copyOnWrite(writeVersion);
                long oldSize = c.getTotalCount();
                result = remove(c, writeVersion, key);
                if (oldSize == c.getTotalCount()) {
                    continue;
                }
                if (c.getTotalCount() == 0) {
                    // this child was deleted
                    p.remove(i);
                    if (p.getKeyCount() == 0) {
                        removePage(p);
                    }
                    break;
                }
                Object oldBounds = p.getKey(i);
                if (!keyType.isInside(key, oldBounds)) {
                    p.setKey(i, getBounds(c));
                }
                p.setChild(i, c);
                break;
            }
        }
        return result;
    }

    private Object getBounds(Page x) {
        Object bounds = keyType.createBoundingBox(x.getKey(0));
        for (int i = 1; i < x.getKeyCount(); i++) {
            keyType.increaseBounds(bounds, x.getKey(i));
        }
        return bounds;
    }

    @SuppressWarnings("unchecked")
    public V put(K key, V value) {
        return (V) putOrAdd(key, value, false);
    }

    /**
     * Add a given key-value pair. The key should not exist (if it exists, the
     * result is undefined).
     *
     * @param key the key
     * @param value the value
     */
    public void add(K key, V value) {
        putOrAdd(key, value, true);
    }

    private Object putOrAdd(K key, V value, boolean alwaysAdd) {
        checkWrite();
        long writeVersion = store.getCurrentVersion();
        Page p = root.copyOnWrite(writeVersion);
        Object result;
        if (alwaysAdd || get(key) == null) {
            if (p.getMemory() > store.getMaxPageSize() && p.getKeyCount() > 1) {
                // only possible if this is the root, else we would have split earlier
                // (this requires maxPageSize is fixed)
                long totalCount = p.getTotalCount();
                Page split = split(p, writeVersion);
                Object k1 = getBounds(p);
                Object k2 = getBounds(split);
                Object[] keys = { k1, k2 };
                long[] children = { p.getPos(), split.getPos(), 0 };
                Page[] childrenPages = { p, split, null };
                long[] counts = { p.getTotalCount(), split.getTotalCount(), 0 };
                p = Page.create(this, writeVersion, 2,
                        keys, null, children, childrenPages, counts,
                        totalCount, 0, 0);
                // now p is a node; continues
            }
            add(p, writeVersion, key, value);
            result = null;
        } else {
            result = set(p, writeVersion, key, value);
        }
        setRoot(p);
        return result;
    }

    /**
     * Update the value for the given key. The key must exist.
     *
     * @param p the page
     * @param writeVersion the write version
     * @param key the key
     * @param value the value
     * @return the old value
     */
    private Object set(Page p, long writeVersion, Object key, Object value) {
        if (!p.isLeaf()) {
            for (int i = 0; i < p.getKeyCount(); i++) {
                if (contains(p, i, key)) {
                    Page c = p.getChildPage(i).copyOnWrite(writeVersion);
                    Object result = set(c, writeVersion, key, value);
                    if (result != null) {
                        p.setChild(i, c);
                        return result;
                    }
                }
            }
        } else {
            for (int i = 0; i < p.getKeyCount(); i++) {
                if (keyType.equals(p.getKey(i), key)) {
                    return p.setValue(i, value);
                }
            }
        }
        return null;
    }

    private void add(Page p, long writeVersion, Object key, Object value) {
        if (p.isLeaf()) {
            p.insertLeaf(p.getKeyCount(), key, value);
            return;
        }
        // p is a node
        int index = -1;
        for (int i = 0; i < p.getKeyCount(); i++) {
            if (contains(p, i, key)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            // a new entry, we don't know where to add yet
            float min = Float.MAX_VALUE;
            for (int i = 0; i < p.getKeyCount(); i++) {
                Object k = p.getKey(i);
                float areaIncrease = keyType.getAreaIncrease(k, key);
                if (areaIncrease < min) {
                    index = i;
                    min = areaIncrease;
                }
            }
        }
        Page c = p.getChildPage(index).copyOnWrite(writeVersion);
        if (c.getMemory() > store.getMaxPageSize() && c.getKeyCount() > 1) {
            // split on the way down
            Page split = split(c, writeVersion);
            p = p.copyOnWrite(writeVersion);
            p.setKey(index, getBounds(c));
            p.setChild(index, c);
            p.insertNode(index, getBounds(split), split);
            // now we are not sure where to add
            add(p, writeVersion, key, value);
            return;
        }
        add(c, writeVersion, key, value);
        Object bounds = p.getKey(index);
        keyType.increaseBounds(bounds, key);
        p.setKey(index, bounds);
        p.setChild(index, c);
    }

    private Page split(Page p, long writeVersion) {
        return quadraticSplit ?
                splitQuadratic(p, writeVersion) :
                splitLinear(p, writeVersion);
    }

    private Page splitLinear(Page p, long writeVersion) {
        ArrayList<Object> keys = New.arrayList();
        for (int i = 0; i < p.getKeyCount(); i++) {
            keys.add(p.getKey(i));
        }
        int[] extremes = keyType.getExtremes(keys);
        if (extremes == null) {
            return splitQuadratic(p, writeVersion);
        }
        Page splitA = newPage(p.isLeaf(), writeVersion);
        Page splitB = newPage(p.isLeaf(), writeVersion);
        move(p, splitA, extremes[0]);
        if (extremes[1] > extremes[0]) {
            extremes[1]--;
        }
        move(p, splitB, extremes[1]);
        Object boundsA = keyType.createBoundingBox(splitA.getKey(0));
        Object boundsB = keyType.createBoundingBox(splitB.getKey(0));
        while (p.getKeyCount() > 0) {
            Object o = p.getKey(0);
            float a = keyType.getAreaIncrease(boundsA, o);
            float b = keyType.getAreaIncrease(boundsB, o);
            if (a < b) {
                keyType.increaseBounds(boundsA, o);
                move(p, splitA, 0);
            } else {
                keyType.increaseBounds(boundsB, o);
                move(p, splitB, 0);
            }
        }
        while (splitB.getKeyCount() > 0) {
            move(splitB, p, 0);
        }
        return splitA;
    }

    private Page splitQuadratic(Page p, long writeVersion) {
        Page splitA = newPage(p.isLeaf(), writeVersion);
        Page splitB = newPage(p.isLeaf(), writeVersion);
        float largest = Float.MIN_VALUE;
        int ia = 0, ib = 0;
        for (int a = 0; a < p.getKeyCount(); a++) {
            Object objA = p.getKey(a);
            for (int b = 0; b < p.getKeyCount(); b++) {
                if (a == b) {
                    continue;
                }
                Object objB = p.getKey(b);
                float area = keyType.getCombinedArea(objA, objB);
                if (area > largest) {
                    largest = area;
                    ia = a;
                    ib = b;
                }
            }
        }
        move(p, splitA, ia);
        if (ia < ib) {
            ib--;
        }
        move(p, splitB, ib);
        Object boundsA = keyType.createBoundingBox(splitA.getKey(0));
        Object boundsB = keyType.createBoundingBox(splitB.getKey(0));
        while (p.getKeyCount() > 0) {
            float diff = 0, bestA = 0, bestB = 0;
            int best = 0;
            for (int i = 0; i < p.getKeyCount(); i++) {
                Object o = p.getKey(i);
                float incA = keyType.getAreaIncrease(boundsA, o);
                float incB = keyType.getAreaIncrease(boundsB, o);
                float d = Math.abs(incA - incB);
                if (d > diff) {
                    diff = d;
                    bestA = incA;
                    bestB = incB;
                    best = i;
                }
            }
            if (bestA < bestB) {
                keyType.increaseBounds(boundsA, p.getKey(best));
                move(p, splitA, best);
            } else {
                keyType.increaseBounds(boundsB, p.getKey(best));
                move(p, splitB, best);
            }
        }
        while (splitB.getKeyCount() > 0) {
            move(splitB, p, 0);
        }
        return splitA;
    }

    private Page newPage(boolean leaf, long writeVersion) {
        Object[] values = leaf ? new Object[4] : null;
        long[] c = leaf ? null : new long[1];
        Page[] cp = leaf ? null : new Page[1];
        return Page.create(this, writeVersion, 0,
                new Object[4], values, c, cp, c, 0, 0, 0);
    }

    private static void move(Page source, Page target, int sourceIndex) {
        Object k = source.getKey(sourceIndex);
        if (source.isLeaf()) {
            Object v = source.getValue(sourceIndex);
            target.insertLeaf(0, k, v);
        } else {
            Page c = source.getChildPage(sourceIndex);
            target.insertNode(0, k, c);
        }
        source.remove(sourceIndex);
    }

    /**
     * Add all node keys (including internal bounds) to the given list.
     * This is mainly used to visualize the internal splits.
     *
     * @param list the list
     * @param p the root page
     */
    @SuppressWarnings("unchecked")
    public void addNodeKeys(ArrayList<K> list, Page p) {
        if (p != null && !p.isLeaf()) {
            for (int i = 0; i < p.getKeyCount(); i++) {
                list.add((K) p.getKey(i));
                addNodeKeys(list, p.getChildPage(i));
            }
        }
    }

    public boolean isQuadraticSplit() {
        return quadraticSplit;
    }

    public void setQuadraticSplit(boolean quadraticSplit) {
        this.quadraticSplit = quadraticSplit;
    }

    protected int getChildPageCount(Page p) {
        return p.getChildPageCount() - 1;
    }

    /**
     * A cursor to iterate over a subset of the keys.
     *
     * @param <K> the key class
     */
    static class RTreeCursor<K> extends Cursor<K> {

        protected RTreeCursor(MVRTreeMap<K, ?> map, Page root, K from) {
            super(map, root, from);
        }

        public void skip(long n) {
            if (!hasNext()) {
                return;
            }
            while (n-- > 0) {
                fetchNext();
            }
        }

        /**
         * Check a given key.
         *
         * @param key the stored key
         * @param test the user-supplied test key
         * @return true if there is a match
         */
        protected boolean check(K key, K test) {
            return true;
        }

        @SuppressWarnings("unchecked")
        protected void min(Page p, K x) {
            while (true) {
                if (p.isLeaf()) {
                    pos = new CursorPos(p, 0, pos);
                    return;
//                    for (int i = 0; i < p.getKeyCount(); i++) {
//                        if (check((K) p.getKey(i), x)) {
//                            pos = new CursorPos(p, i, pos);
//                            return;
//                        }
//                    }
//                    break;
                }
                boolean found = false;
                for (int i = 0; i < p.getKeyCount(); i++) {
                    if (check((K) p.getKey(i), x)) {
                        pos = new CursorPos(p, i + 1, pos);
                        p = p.getChildPage(i);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    break;
                }
            }
            fetchNext();
        }

        @SuppressWarnings("unchecked")
        protected void fetchNext() {
            while (pos != null) {
                while (pos.index < pos.page.getKeyCount()) {
                    K k = (K) pos.page.getKey(pos.index++);
                    if (check(k, from)) {
                        current = k;
                        return;
                    }
                }
                pos = pos.parent;
                if (pos == null) {
                    break;
                }
                MVRTreeMap<K, ?> m = (MVRTreeMap<K, ?>) map;
                if (pos.index < m.getChildPageCount(pos.page)) {
                    min(pos.page.getChildPage(pos.index++), from);
                }
            }
            current = null;
        }
    }

}
