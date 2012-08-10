/*
 * Copyright 2004-2011 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.dev.store.btree;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * A chunk of data, containing one or multiple pages.
 * <p>
 * Chunks are page aligned (each page is usually 4096 bytes).
 * There are at most 67 million (2^26) chunks,
 * each chunk is at most 2 GB large.
 * File format:
 * 1 byte: 'c'
 * 4 bytes: length
 * 4 bytes: chunk id (an incrementing number)
 * 8 bytes: metaRootPos
 * [ Page ] *
 */
class Chunk {

    /**
     * The chunk id.
     */
    int id;

    /**
     * The start position within the file.
     */
    long start;

    /**
     * The length in bytes (may be larger than the actual value).
     */
    long length;

    /**
     * The entry count.
     */
    int entryCount;

    /**
     * The number of life (non-garbage) objects.
     */
    int liveCount;

    /**
     * The garbage collection priority.
     */
    int collectPriority;

    /**
     * The position of the meta root.
     */
    long metaRootPos;

    /**
     * The version stored in this chunk.
     */
    long version;

    Chunk(int id) {
        this.id = id;
    }

    /**
     * Build a block from the given string.
     *
     * @param s the string
     * @return the block
     */
    static Chunk fromString(String s) {
        Properties prop = new Properties();
        try {
            prop.load(new ByteArrayInputStream(s.getBytes("UTF-8")));
            int id = Integer.parseInt(prop.get("id").toString());
            Chunk c = new Chunk(id);
            c.start = Long.parseLong(prop.get("start").toString());
            c.length = Long.parseLong(prop.get("length").toString());
            c.entryCount = Integer.parseInt(prop.get("entryCount").toString());
            c.liveCount = Integer.parseInt(prop.get("liveCount").toString());
            c.metaRootPos = Long.parseLong(prop.get("metaRoot").toString());
            c.version = Long.parseLong(prop.get("version").toString());
            return c;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public int getFillRate() {
        return entryCount == 0 ? 0 : 100 * liveCount / entryCount;
    }

    public int hashCode() {
        return id;
    }

    public boolean equals(Object o) {
        return o instanceof Chunk && ((Chunk) o).id == id;
    }

    public String toString() {
        return
            "id:" + id + "\n" +
            "start:" + start + "\n" +
            "length:" + length + "\n" +
            "entryCount:" + entryCount + "\n" +
            "liveCount:" + liveCount + "\n" +
            "metaRoot:" + metaRootPos + "\n" +
            "version:" + version + "\n";
    }

}

