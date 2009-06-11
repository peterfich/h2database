/*
 * Copyright 2004-2009 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.store;

import java.sql.SQLException;
import org.h2.constant.ErrorCode;
import org.h2.index.Page;
import org.h2.message.Message;

/**
 * A trunk page of a stream. It contains the page numbers of the stream, and
 * the page number of the next trunk. The format is:
 * <ul>
 * <li>0-3: the last trunk page, or 0 if none</li>
 * <li>4-4: page type</li>
 * <li>5-8: the next trunk page</li>
 * <li>9-12: the number of pages</li>
 * <li>13-remainder: page ids</li>
 * </ul>
 */
public class PageStreamTrunk extends Record {

    private static final int DATA_START = 13;

    private final PageStore store;
    private int parent;
    private int nextTrunk;
    private int[] pageIds;
    private int pageCount;
    private DataPage data;
    private int index;

    PageStreamTrunk(PageStore store, int parent, int pageId, int next, int[] pageIds) {
        setPos(pageId);
        this.parent = parent;
        this.store = store;
        this.nextTrunk = next;
        this.pageCount = pageIds.length;
        this.pageIds = pageIds;
    }

    public PageStreamTrunk(PageStore store, int pageId) {
        setPos(pageId);
        this.store = store;
    }

    /**
     * Read the page from the disk.
     */
    void read() throws SQLException {
        data = store.createDataPage();
        store.readPage(getPos(), data);
        parent = data.readInt();
        int t = data.readByte();
        if (t != Page.TYPE_STREAM_TRUNK) {
            throw Message.getSQLException(ErrorCode.FILE_CORRUPTED_1, "pos:" + getPos() + " type:" + t + " parent:" + parent
                    + " expected type:" + Page.TYPE_STREAM_TRUNK);
        }
        nextTrunk = data.readInt();
        pageCount = data.readInt();
        for (int i = 0; i < pageCount; i++) {
            pageIds[i] = data.readInt();
        }
    }

    void setNextPage(int page) {
        pageIds[index++] = page;
    }

    int getNextPage() {
        if (index >= pageIds.length) {
            return -1;
        }
        return pageIds[index++];
    }

    int getNextTrunk() {
        return nextTrunk;
    }

    public int getByteCount(DataPage dummy) {
        return store.getPageSize();
    }

    public void write(DataPage buff) throws SQLException {
        data = store.createDataPage();
        data.writeInt(parent);
        data.writeByte((byte) Page.TYPE_STREAM_TRUNK);
        data.writeInt(nextTrunk);
        data.writeInt(pageCount);
        for (int i = 0; i < pageCount; i++) {
            data.writeInt(pageIds[i]);
        }
        store.writePage(getPos(), data);
    }

    /**
     * Get the number of pages that can be addressed in a stream trunk page.
     *
     * @param pageSize the page size
     * @return the number of pages
     */
    static int getPagesAddressed(int pageSize) {
        return (pageSize - DATA_START) / 4;
    }

}
