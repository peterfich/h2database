/*
 * Copyright 2004-2013 H2 Group. Multiple-Licensed under the H2 License, Version
 * 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html). Initial Developer: H2 Group
 */
package org.h2.test.store;

import java.util.Random;
import java.util.TreeMap;

import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.h2.store.fs.FileUtils;
import org.h2.test.TestBase;

/**
 * Tests the MVStore.
 */
public class TestRandomMapOps extends TestBase {

    private String fileName;
    private int seed;
    private int op;

    /**
     * Run just this test.
     * 
     * @param a ignored
     */
    public static void main(String... a) throws Exception {
        TestBase.createCaller().init().test();
    }

    @Override
    public void test() throws Exception {
        test("memFS:randomOps.h3");
        int todoTestConcurrentMap;
        int todoTestMVRTreeMap;
    }

    public void test(String fileName) {
        this.fileName = fileName;
        int best = Integer.MAX_VALUE;
        int bestSeed = 0;
        Throwable failException = null;
        for (seed = 0; seed < 1000; seed++) {
            FileUtils.delete(fileName);
            Throwable ex = null;
            try {
                testCase();
                continue;
            } catch (Exception e) {
                ex = e;
            } catch (AssertionError e) {
                ex = e;
            }
            if (op < best) {
                trace(seed);
                bestSeed = seed;
                best = op;
                failException = ex;
            }
        }
        if (failException != null) {
            throw (AssertionError) new AssertionError("seed = " + bestSeed
                    + " op = " + best).initCause(failException);
        }
    }

    private void testCase() throws Exception {
        FileUtils.delete(fileName);
        MVStore s;
        MVMap<Integer, byte[]> m;

        s = new MVStore.Builder().fileName(fileName).
                pageSplitSize(50).writeDelay(0).open();
        m = s.openMap("data");
        
        Random r = new Random(seed);
        op = 0;
        int size = getSize(10, 100);
        TreeMap<Integer, byte[]> map = new TreeMap<Integer, byte[]>();
        for (; op < size; op++) {
            int k = r.nextInt(100);
            byte[] v = new byte[r.nextInt(10) * 10];
            int type = r.nextInt(11);
            switch (type) {
            case 0:
            case 1:
            case 2:
            case 3:
                log(op, k, v, "put");
                m.put(k, v);
                map.put(k, v);
                break;
            case 4:
            case 5:
                log(op, k, v, "remove");
                m.remove(k);
                map.remove(k);
                break;
            case 6:
                log(op, k, v, "store");
                s.store();
                break;
            case 7:
                log(op, k, v, "compact");
                s.compact(80);
                break;
            case 8:
                log(op, k, v, "clear");
                m.clear();
                map.clear();
                break;
            case 9:
                log(op, k, v, "commit");
                s.commit();
                break;
            case 10:
                log(op, k, v, "reopen");
                s.commit();
                s.close();
                s = new MVStore.Builder().fileName(fileName).
                        pageSplitSize(50).writeDelay(0).open();
                m = s.openMap("data");
                break;
            }
            assertEqualsMapValues(map.get(k), m.get(k));
            assertEquals(map.ceilingKey(k), m.ceilingKey(k));
            assertEquals(map.floorKey(k), m.floorKey(k));
            assertEquals(map.higherKey(k), m.higherKey(k));
            assertEquals(map.lowerKey(k), m.lowerKey(k));
            assertEquals(map.isEmpty(), m.isEmpty());
            if (map.size() != m.size()) {
                assertEquals(map.size(), m.size());
            }
        }
        s.store();
        s.close();
    }
    
    private void assertEqualsMapValues(byte[] x, byte[] y) {
        if (x == null || y == null) {
            if (x != y) {
                assertTrue(x == y);
            }
        } else {
            assertEquals(x.length, y.length);
        }
    }
    
    private static void log(int op, int k, byte[] v, String msg) {
         // System.out.println(op + ": " + msg + " key: " + k + " value: " + v);
    }

}
