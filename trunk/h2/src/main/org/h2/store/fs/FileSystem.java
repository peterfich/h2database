/*
 * Copyright 2004-2011 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.store.fs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import org.h2.util.MathUtils;
import org.h2.util.New;
import org.h2.util.StringUtils;

/**
 * The file system is a storage abstraction.
 */
public abstract class FileSystem {

    /**
     * The prefix for temporary files. See also TestClearReferences.
     */
    private static String tempRandom;
    private static long tempSequence;

    private static boolean defaultServicesRegistered;

    private static final ArrayList<FileSystem> SERVICES = New.arrayList();

    /**
     * Get the file system object.
     *
     * @param fileName the file name or prefix
     * @return the file system
     */
    public static FileSystem getInstance(String fileName) {
        if (fileName.indexOf(':') >= 0) {
            if (FileSystemMemory.getInstance().accepts(fileName)) {
                return FileSystemMemory.getInstance();
            }
            registerDefaultServices();
            for (FileSystem fs : SERVICES) {
                if (fs.accepts(fileName)) {
                    return fs;
                }
            }
        }
        return FileSystemDisk.getInstance();
    }

    private static synchronized void registerDefaultServices() {
        if (!defaultServicesRegistered) {
            defaultServicesRegistered = true;
            for (String c : new String[] {
                    "org.h2.store.fs.FileSystemZip",
                    "org.h2.store.fs.FileSystemSplit",
                    "org.h2.store.fs.FileSystemDiskNio",
                    "org.h2.store.fs.FileSystemDiskNioMapped"
            }) {
                try {
                    Class.forName(c);
                } catch (Exception e) {
                    // ignore - the files may be excluded in purpose
                }
            }
        }
    }

    /**
     * Register a file system.
     *
     * @param service the file system
     */
    public static synchronized void register(FileSystem service) {
        registerDefaultServices();
        SERVICES.add(service);
    }

    /**
     * Unregister a file system.
     *
     * @param service the file system
     */
    public static synchronized void unregister(FileSystem service) {
        SERVICES.remove(service);
    }

    /**
     * Check if the file system is responsible for this file name.
     *
     * @param fileName the file name
     * @return true if it is
     */
    protected abstract boolean accepts(String fileName);

    /**
     * Get the size of a file in bytes
     *
     * @param fileName the file name
     * @return the size in bytes
     */
    public abstract long size(String fileName);

    /**
     * Rename a file if this is allowed.
     *
     * @param oldName the old fully qualified file name
     * @param newName the new fully qualified file name
     */
    public abstract void moveTo(String oldName, String newName);

    /**
     * Create a new file.
     *
     * @param fileName the file name
     * @return true if creating was successful
     */
    public abstract boolean createFile(String fileName);

    /**
     * Checks if a file exists.
     *
     * @param fileName the file name
     * @return true if it exists
     */
    public abstract boolean exists(String fileName);

    /**
     * Delete a file or directory if it exists.
     * Directories may only be deleted if they are empty.
     *
     * @param path the file or directory name
     */
    public abstract void delete(String path);

    /**
     * List the files in the given directory.
     *
     * @param directory the directory
     * @return the list of fully qualified file names
     */
    public abstract String[] listFiles(String directory);

    /**
     * Check if a file is read-only.
     *
     * @param fileName the file name
     * @return if it is read only
     */
    public abstract boolean isReadOnly(String fileName);

    /**
     * Normalize a file name.
     *
     * @param fileName the file name
     * @return the normalized file name
     */
    public abstract String getCanonicalPath(String fileName);

    /**
     * Get the parent directory of a file or directory.
     *
     * @param fileName the file or directory name
     * @return the parent directory name
     */
    public abstract String getParent(String fileName);

    /**
     * Check if it is a file or a directory.
     *
     * @param fileName the file or directory name
     * @return true if it is a directory
     */
    public abstract boolean isDirectory(String fileName);

    /**
     * Check if the file name includes a path.
     *
     * @param fileName the file name
     * @return if the file name is absolute
     */
    public abstract boolean isAbsolute(String fileName);

    /**
     * Get the last modified date of a file
     *
     * @param fileName the file name
     * @return the last modified date
     */
    public abstract long lastModified(String fileName);

    /**
     * Check if the file is writable.
     *
     * @param fileName the file name
     * @return if the file is writable
     */
    public abstract boolean canWrite(String fileName);

    /**
     * Create a directory (all required parent directories already exist).
     *
     * @param directoryName the directory name
     */
    public abstract void createDirectory(String directoryName);

    /**
     * Get the file or directory name (the last element of the path).
     *
     * @param path the directory and file name
     * @return just the file name
     */
    public abstract String getName(String path);

    /**
     * Check if a file starts with a given prefix.
     *
     * @param fileName the complete file name
     * @param prefix the prefix
     * @return true if it starts with the prefix
     */
    public abstract boolean fileStartsWith(String fileName, String prefix);

    /**
     * Create an output stream to write into the file.
     *
     * @param fileName the file name
     * @param append if true, the file will grow, if false, the file will be
     *            truncated first
     * @return the output stream
     */
    public abstract OutputStream newOutputStream(String fileName, boolean append);

    /**
     * Open a random access file object.
     *
     * @param fileName the file name
     * @param mode the access mode. Supported are r, rw, rws, rwd
     * @return the file object
     */
    public abstract FileObject openFileObject(String fileName, String mode) throws IOException;

    /**
     * Create an input stream to read from the file.
     *
     * @param fileName the file name
     * @return the input stream
     */
    public abstract InputStream newInputStream(String fileName) throws IOException;

    /**
     * Disable the ability to write.
     *
     * @param fileName the file name
     * @return true if the call was successful
     */
    public abstract boolean setReadOnly(String fileName);

    /**
     * Get the next temporary file name part (the part in the middle).
     *
     * @param newRandom if the random part of the filename should change
     * @return the file name part
     */
    protected static synchronized String getNextTempFileNamePart(boolean newRandom) {
        if (newRandom || tempRandom == null) {
            byte[] prefix = new byte[8];
            MathUtils.randomBytes(prefix);
            tempRandom = StringUtils.convertBytesToHex(prefix) + ".";
        }
        return tempRandom + tempSequence++;
    }

    /**
     * Create a new temporary file.
     *
     * @param prefix the prefix of the file name (including directory name if
     *            required)
     * @param suffix the suffix
     * @param deleteOnExit if the file should be deleted when the virtual
     *            machine exists
     * @param inTempDir if the file should be stored in the temporary directory
     * @return the name of the created file
     */
    public String createTempFile(String prefix, String suffix, boolean deleteOnExit, boolean inTempDir) throws IOException {
        while (true) {
            String n = prefix + getNextTempFileNamePart(false) + suffix;
            if (exists(n)) {
                // in theory, the random number could collide
                getNextTempFileNamePart(true);
            }
            // creates the file (not thread safe)
            openFileObject(n, "rw").close();
            return n;
        }
    }

    /**
     * Get the unwrapped file name (without wrapper prefixes if wrapping /
     * delegating file systems are used).
     *
     * @param fileName the file name
     * @return the unwrapped
     */
    public abstract String unwrap(String fileName);

}
