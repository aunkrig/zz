package org.incava.util.diff;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.TreeMap;


/**
 * Compares two lists, returning a list of the additions, changes, and deletions
 * between them. A <code>Comparator</code> may be passed as an argument to the
 * constructor, and will thus be used. If not provided, the initial value in the
 * <code>a</code> ("from") list will be looked at to see if it supports the
 * <code>Comparable</code> interface. If so, its <code>equals</code> and
 * <code>compareTo</code> methods will be invoked on the instances in the "from"
 * and "to" lists; otherwise, for speed, hash codes from the objects will be
 * used instead for comparison.
 *
 * <p>The file FileDiff.java shows an example usage of this class, in an
 * application similar to the Unix "diff" program.</p>
 */
@SuppressWarnings("all")
public class Diff<Type>
{
    /**
     * The source list, AKA the "from" values.
     */
    protected List<Type> a;

    /**
     * The target list, AKA the "to" values.
     */
    protected List<Type> b;

    /**
     * The list of differences, as <code>Difference</code> instances.
     */
    protected List<Difference> diffs = new ArrayList<Difference>();

    /**
     * The pending, uncommitted difference.
     */
    private Difference pending;

    /**
     * The comparator used, if any.
     */
    private Comparator<Type> comparator;

    /**
     * The thresholds.
     */
    private TreeMap<Integer, Integer> thresh;

    /**
     * Constructs the Diff object for the two arrays, using the given comparator.
     */
    public Diff(Type[] a, Type[] b, Comparator<Type> comp)
    {
        this(Arrays.asList(a), Arrays.asList(b), comp);
    }

    /**
     * Constructs the Diff object for the two arrays, using the default
     * comparison mechanism between the objects, such as <code>equals</code> and
     * <code>compareTo</code>.
     */
    public Diff(Type[] a, Type[] b)
    {
        this(a, b, null);
    }

    /**
     * Constructs the Diff object for the two lists, using the given comparator.
     */
    public Diff(List<Type> a, List<Type> b, Comparator<Type> comp)
    {
        this.a = a;
        this.b = b;
        this.comparator = comp;
        this.thresh = null;
    }

    /**
     * Constructs the Diff object for the two lists, using the default
     * comparison mechanism between the objects, such as <code>equals</code> and
     * <code>compareTo</code>.
     */
    public Diff(List<Type> a, List<Type> b)
    {
        this(a, b, null);
    }

    /**
     * Runs diff and returns the results.
     */
    public List<Difference> diff()
    {
        this.traverseSequences();

        // add the last difference, if pending:
        if (this.pending != null) {
            this.diffs.add(this.pending);
        }

        return this.diffs;
    }

    /**
     * Traverses the sequences, seeking the longest common subsequences,
     * invoking the methods <code>finishedA</code>, <code>finishedB</code>,
     * <code>onANotB</code>, and <code>onBNotA</code>.
     */
    protected void traverseSequences()
    {
        Integer[] matches = this.getLongestCommonSubsequences();

        int lastA = this.a.size() - 1;
        int lastB = this.b.size() - 1;
        int bi = 0;
        int ai;

        int lastMatch = matches.length - 1;

        for (ai = 0; ai <= lastMatch; ++ai) {
            Integer bLine = matches[ai];

            if (bLine == null) {
                this.onANotB(ai, bi);
            }
            else {
                while (bi < bLine) {
                    this.onBNotA(ai, bi++);
                }

                this.onMatch(ai, bi++);
            }
        }

        boolean calledFinishA = false;
        boolean calledFinishB = false;

        while (ai <= lastA || bi <= lastB) {

            // last A?
            if (ai == lastA + 1 && bi <= lastB) {
                if (!calledFinishA && this.callFinishedA()) {
                    this.finishedA(lastA);
                    calledFinishA = true;
                }
                else {
                    while (bi <= lastB) {
                        this.onBNotA(ai, bi++);
                    }
                }
            }

            // last B?
            if (bi == lastB + 1 && ai <= lastA) {
                if (!calledFinishB && this.callFinishedB()) {
                    this.finishedB(lastB);
                    calledFinishB = true;
                }
                else {
                    while (ai <= lastA) {
                        this.onANotB(ai++, bi);
                    }
                }
            }

            if (ai <= lastA) {
                this.onANotB(ai++, bi);
            }

            if (bi <= lastB) {
                this.onBNotA(ai, bi++);
            }
        }
    }

    /**
     * Override and return true in order to have <code>finishedA</code> invoked
     * at the last element in the <code>a</code> array.
     */
    protected boolean callFinishedA()
    {
        return false;
    }

    /**
     * Override and return true in order to have <code>finishedB</code> invoked
     * at the last element in the <code>b</code> array.
     */
    protected boolean callFinishedB()
    {
        return false;
    }

    /**
     * Invoked at the last element in <code>a</code>, if
     * <code>callFinishedA</code> returns true.
     */
    protected void finishedA(int lastA)
    {
    }

    /**
     * Invoked at the last element in <code>b</code>, if
     * <code>callFinishedB</code> returns true.
     */
    protected void finishedB(int lastB)
    {
    }

    /**
     * Invoked for elements in <code>a</code> and not in <code>b</code>.
     */
    protected void onANotB(int ai, int bi)
    {
        if (this.pending == null) {
            this.pending = new Difference(ai, ai, bi, -1);
        }
        else {
            this.pending.setDeleted(ai);
        }
    }

    /**
     * Invoked for elements in <code>b</code> and not in <code>a</code>.
     */
    protected void onBNotA(int ai, int bi)
    {
        if (this.pending == null) {
            this.pending = new Difference(ai, -1, bi, bi);
        }
        else {
            this.pending.setAdded(bi);
        }
    }

    /**
     * Invoked for elements matching in <code>a</code> and <code>b</code>.
     */
    protected void onMatch(int ai, int bi)
    {
        if (this.pending == null) {
            // no current pending
        }
        else {
            this.diffs.add(this.pending);
            this.pending = null;
        }
    }

    /**
     * Compares the two objects, using the comparator provided with the
     * constructor, if any.
     */
    protected boolean equals(Type x, Type y)
    {
        return this.comparator == null ? x.equals(y) : this.comparator.compare(x, y) == 0;
    }

    /**
     * Returns an array of the longest common subsequences.
     */
    public Integer[] getLongestCommonSubsequences()
    {
        int aStart = 0;
        int aEnd = this.a.size() - 1;

        int bStart = 0;
        int bEnd = this.b.size() - 1;

        TreeMap<Integer, Integer> matches = new TreeMap<Integer, Integer>();

        while (aStart <= aEnd && bStart <= bEnd && this.equals(this.a.get(aStart), this.b.get(bStart))) {
            matches.put(aStart++, bStart++);
        }

        while (aStart <= aEnd && bStart <= bEnd && this.equals(this.a.get(aEnd), this.b.get(bEnd))) {
            matches.put(aEnd--, bEnd--);
        }

        Map<Type, List<Integer>> bMatches = null;
        if (this.comparator == null) {
            if (this.a.size() > 0 && this.a.get(0) instanceof Comparable<?>) {
                // this uses the Comparable interface
                bMatches = new TreeMap<Type, List<Integer>>();
            }
            else {
                // this just uses hashCode()
                bMatches = new HashMap<Type, List<Integer>>();
            }
        }
        else {
            // we don't really want them sorted, but this is the only Map
            // implementation (as of JDK 1.4) that takes a comparator.
            bMatches = new TreeMap<Type, List<Integer>>(this.comparator);
        }

        for (int bi = bStart; bi <= bEnd; ++bi) {
            Type         element    = this.b.get(bi);
            Type          key       = element;
            List<Integer> positions = bMatches.get(key);

            if (positions == null) {
                positions = new ArrayList<Integer>();
                bMatches.put(key, positions);
            }

            positions.add(bi);
        }

        this.thresh = new TreeMap<Integer, Integer>();
        Map<Integer, Object[]> links = new HashMap<Integer, Object[]>();

        for (int i = aStart; i <= aEnd; ++i) {
            Type aElement  = this.a.get(i);
            List<Integer> positions = bMatches.get(aElement);

            if (positions != null) {
                Integer  k   = 0;
                ListIterator<Integer> pit = positions.listIterator(positions.size());
                while (pit.hasPrevious()) {
                    Integer j = pit.previous();

                    k = this.insert(j, k);

                    if (k == null) {
                        // nothing
                    }
                    else {
                        Object value = k > 0 ? links.get(k - 1) : null;
                        links.put(k, new Object[] { value, i, j });
                    }
                }
            }
        }

        if (this.thresh.size() > 0) {
            Integer  ti   = this.thresh.lastKey();
            Object[] link = links.get(ti);
            while (link != null) {
                Integer x = (Integer)link[1];
                Integer y = (Integer)link[2];
                matches.put(x, y);
                link = (Object[])link[0];
            }
        }

        int       size = matches.size() == 0 ? 0 : 1 + matches.lastKey();
        Integer[] ary  = new Integer[size];
        for (Integer idx : matches.keySet()) {
            Integer val = matches.get(idx);
            ary[idx] = val;
        }
        return ary;
    }

    /**
     * Returns whether the integer is not zero (including if it is not null).
     */
    protected static boolean isNonzero(Integer i)
    {
        return i != null && i != 0;
    }

    /**
     * Returns whether the value in the map for the given index is greater than
     * the given value.
     */
    protected boolean isGreaterThan(Integer index, Integer val)
    {
        Integer lhs = this.thresh.get(index);
        return lhs != null && val != null && lhs.compareTo(val) > 0;
    }

    /**
     * Returns whether the value in the map for the given index is less than
     * the given value.
     */
    protected boolean isLessThan(Integer index, Integer val)
    {
        Integer lhs = this.thresh.get(index);
        return lhs != null && (val == null || lhs.compareTo(val) < 0);
    }

    /**
     * Returns the value for the greatest key in the map.
     */
    protected Integer getLastValue()
    {
        return this.thresh.get(this.thresh.lastKey());
    }

    /**
     * Adds the given value to the "end" of the threshold map, that is, with the
     * greatest index/key.
     */
    protected void append(Integer value)
    {
        Integer addIdx = null;
        if (this.thresh.size() == 0) {
            addIdx = 0;
        }
        else {
            Integer lastKey = this.thresh.lastKey();
            addIdx = lastKey + 1;
        }
        this.thresh.put(addIdx, value);
    }

    /**
     * Inserts the given values into the threshold map.
     */
    protected Integer insert(Integer j, Integer k)
    {
        if (Diff.isNonzero(k) && this.isGreaterThan(k, j) && this.isLessThan(k - 1, j)) {
            this.thresh.put(k, j);
        }
        else {
            int high = -1;

            if (Diff.isNonzero(k)) {
                high = k;
            }
            else if (this.thresh.size() > 0) {
                high = this.thresh.lastKey();
            }

            // off the end?
            if (high == -1 || j.compareTo(this.getLastValue()) > 0) {
                this.append(j);
                k = high + 1;
            }
            else {
                // binary search for insertion point:
                int low = 0;

                while (low <= high) {
                    int     index = (high + low) / 2;
                    Integer val   = this.thresh.get(index);
                    int     cmp   = j.compareTo(val);

                    if (cmp == 0) {
                        return null;
                    }
                    else if (cmp > 0) {
                        low = index + 1;
                    }
                    else {
                        high = index - 1;
                    }
                }

                this.thresh.put(low, j);
                k = low;
            }
        }

        return k;
    }
}
