/*
 * Copyright 2004-2014 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.mvstore;

import java.util.Iterator;


/**
 * A very simple linked list that supports concurrent access. 
 * Internally, it uses immutable objects. 
 * It uses recursion and is not meant for long lists.
 * 
 * @param <K> the key type
 */
public class ConcurrentLinkedList<K> {
    
    static final Entry<?> NULL = new Entry<Object>(null, null);

    @SuppressWarnings("unchecked")
    volatile Entry<K> head = (Entry<K>) NULL;
    
    public K peekFirst() {
        Entry<K> x = head;
        return x.obj;
    }

    public K peekLast() {
        Entry<K> x = head;
        while (x != NULL && x.next != NULL) {
            x = x.next;
        }
        return x.obj;
    }

    public synchronized void add(K obj) {
        head = Entry.append(head, obj);
    }
    
    public synchronized boolean removeFirst(K obj) {
        if (head.obj != obj) {
            return false;
        }
        head = head.next;
        return true;
    }

    public synchronized boolean removeLast(K obj) {
        if (peekLast() != obj) {
            return false;
        }
        head = Entry.removeLast(head);
        return true;
    }

    public Iterator<K> iterator() {
        return new Iterator<K>() {

            Entry<K> current = head;

            @Override
            public boolean hasNext() {
                return current != NULL;
            }

            @Override
            public K next() {
                K x = current.obj;
                current = current.next;
                return x;
            }

            @Override
            public void remove() {
                throw DataUtils.newUnsupportedOperationException("remove");
            }

        };
    }

    /**
     * An entry in the linked list.
     */
    private static class Entry<K> {
        final K obj;
        Entry<K> next;
        
        Entry(K obj, Entry<K> next) {
            this.obj = obj;
            this.next = next;
        }

        @SuppressWarnings("unchecked")
        static <K> Entry<K> append(Entry<K> list, K obj) {
            if (list == NULL) {
                return new Entry<K>(obj, (Entry<K>) NULL);
            }
            return new Entry<K>(list.obj, append(list.next, obj));
        }
        
        @SuppressWarnings("unchecked")
        static <K> Entry<K> removeLast(Entry<K> list) {
            if (list == NULL || list.next == NULL) {
                return (Entry<K>) NULL;
            }
            return new Entry<K>(list.obj, removeLast(list.next));
        }

    }

}
