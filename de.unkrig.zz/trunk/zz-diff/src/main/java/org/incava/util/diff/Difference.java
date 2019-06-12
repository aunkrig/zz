package org.incava.util.diff;

/**
 * Represents a difference, as used in <code>Diff</code>. A difference consists
 * of two pairs of starting and ending points, each pair representing either the
 * "from" or the "to" collection passed to <code>Diff</code>. If an ending point
 * is -1, then the difference was either a deletion or an addition. For example,
 * if <code>getDeletedEnd()</code> returns -1, then the difference represents an
 * addition.
 */
@SuppressWarnings("all")
public class Difference
{
    public static final int NONE = -1;

    /**
     * The point at which the deletion starts.
     */
    private int delStart = Difference.NONE;

    /**
     * The point at which the deletion ends.
     */
    private int delEnd = Difference.NONE;

    /**
     * The point at which the addition starts.
     */
    private int addStart = Difference.NONE;

    /**
     * The point at which the addition ends.
     */
    private int addEnd = Difference.NONE;

    /**
     * Creates the difference for the given start and end points for the
     * deletion and addition.
     */
    public Difference(int delStart, int delEnd, int addStart, int addEnd)
    {
        this.delStart = delStart;
        this.delEnd   = delEnd;
        this.addStart = addStart;
        this.addEnd   = addEnd;
    }

    /**
     * The point at which the deletion starts, if any. A value equal to
     * <code>NONE</code> means this is an addition.
     */
    public int getDeletedStart()
    {
        return this.delStart;
    }

    /**
     * The point at which the deletion ends, if any. A value equal to
     * <code>NONE</code> means this is an addition.
     */
    public int getDeletedEnd()
    {
        return this.delEnd;
    }

    /**
     * The point at which the addition starts, if any. A value equal to
     * <code>NONE</code> means this must be an addition.
     */
    public int getAddedStart()
    {
        return this.addStart;
    }

    /**
     * The point at which the addition ends, if any. A value equal to
     * <code>NONE</code> means this must be an addition.
     */
    public int getAddedEnd()
    {
        return this.addEnd;
    }

    /**
     * Sets the point as deleted. The start and end points will be modified to
     * include the given line.
     */
    public void setDeleted(int line)
    {
        this.delStart = Math.min(line, this.delStart);
        this.delEnd   = Math.max(line, this.delEnd);
    }

    /**
     * Sets the point as added. The start and end points will be modified to
     * include the given line.
     */
    public void setAdded(int line)
    {
        this.addStart = Math.min(line, this.addStart);
        this.addEnd   = Math.max(line, this.addEnd);
    }

    /**
     * Compares this object to the other for equality. Both objects must be of
     * type Difference, with the same starting and ending points.
     */
    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof Difference) {
            Difference other = (Difference)obj;

            return (this.delStart == other.delStart &&
                    this.delEnd   == other.delEnd &&
                    this.addStart == other.addStart &&
                    this.addEnd   == other.addEnd);
        }
        else {
            return false;
        }
    }

    /**
     * Returns a string representation of this difference.
     */
    @Override
    public String toString()
    {
        StringBuffer buf = new StringBuffer();
        buf.append("del: [" + this.delStart + ", " + this.delEnd + "]");
        buf.append(" ");
        buf.append("add: [" + this.addStart + ", " + this.addEnd + "]");
        return buf.toString();
    }

}
