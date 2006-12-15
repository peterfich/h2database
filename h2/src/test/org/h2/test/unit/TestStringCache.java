/*
 * Copyright 2004-2006 H2 Group. Licensed under the H2 License, Version 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test.unit;

import java.util.Random;

import org.h2.test.TestBase;
import org.h2.util.StringCache;

public class TestStringCache extends TestBase {
    
    public static void main(String[] args) throws Exception {
        new TestStringCache().runBenchmark();
    }
    
    private void runBenchmark() throws Exception {
        returnNew = false;
        for(int i=0; i<6; i++) {
            useIntern = (i % 2) == 1;
            long time = System.currentTimeMillis();
            testSingleThread(100000);
            time = System.currentTimeMillis()-time;
            System.out.println(time + " ms (useIntern=" + useIntern+")");
        }
        
    }

    private Random random = new Random(1);
    private String[] some = new String[] { null, "", "ABC", "this is a medium sized string", "1", "2"};
    private volatile boolean stop;
    private boolean returnNew;    
    private boolean useIntern;

    public void test() throws Exception {
        returnNew = true;
        StringCache.clearCache();
        testSingleThread(getSize(5000, 20000));
        testMultiThreads();
        returnNew = false;
        StringCache.clearCache();
        testSingleThread(getSize(5000, 20000));
        testMultiThreads();
    }
    
    String randomString() {
        if(random.nextBoolean()) {
            String s = some[random.nextInt(some.length)];
            if(s != null && random.nextBoolean()) {
                s = new String(s);
            }
            return s;
        } else {
            int len = random.nextBoolean() ? random.nextInt(1000) : random.nextInt(10);
            StringBuffer buff = new StringBuffer(len);
            for(int i=0; i<len; i++) {
                buff.append(random.nextInt(0xfff));
            }
            return buff.toString();
        }
    }
    
    void testString() {
        String a = randomString();
        if(returnNew) {
            String b = StringCache.getNew(a);
            try {
                check(a, b);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if(a != null && a == b && a.length()>0) {
                throw new Error("a=" + System.identityHashCode(a) + " b=" + System.identityHashCode(b));
            }
        } else {
            String b;
            if(useIntern) {
                b = a == null ? null : a.intern();
            } else {
                b = StringCache.get(a);
            }
            try {
                check(a, b);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    private void testSingleThread(int len) throws Exception {
        for(int i=0; i<len; i++) {
            testString();
        }
    }

    private void testMultiThreads() throws Exception {
        int threadCount = getSize(3, 100);
        Thread[] threads = new Thread[threadCount];
        for(int i=0; i<threadCount; i++) {
            Thread t = new Thread(new Runnable() {
                public void run() {
                    while(!stop) {
                        testString();
                    }
                }
            });
            threads[i] = t;
        }
        for(int i=0; i<threadCount; i++) {
            threads[i].start();
        }
        int wait = getSize(200, 2000);
        Thread.sleep(wait);
        stop = true;
        for(int i=0; i<threadCount; i++) {
            threads[i].join();
        }
    }

}
