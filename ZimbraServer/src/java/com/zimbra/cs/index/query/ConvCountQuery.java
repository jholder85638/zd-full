/*
 * 
 */
package com.zimbra.cs.index.query;

import com.zimbra.cs.index.DBQueryOperation;
import com.zimbra.cs.index.QueryOperation;

/**
 * Query by conversation count.
 *
 * @author tim
 * @author ysasaki
 */
public final class ConvCountQuery extends Query {
    private int mLowestCount;
    private boolean mLowerEq;
    private int mHighestCount;
    private boolean mHigherEq;

    private ConvCountQuery(int lowestCount, boolean lowerEq,
            int highestCount, boolean higherEq) {
        mLowestCount = lowestCount;
        mLowerEq = lowerEq;
        mHighestCount = highestCount;
        mHigherEq = higherEq;
    }

    @Override
    public void dump(StringBuilder out) {
        out.append("ConvCount");
        out.append(mLowerEq ? ">=" : ">");
        out.append(mLowestCount);
        out.append(' ');
        out.append(mHigherEq? "<=" : "<");
        out.append(mHighestCount);
    }

    @Override
    public QueryOperation getQueryOperation(boolean bool) {
        DBQueryOperation op = new DBQueryOperation();
        op.addConvCountClause(mLowestCount, mLowerEq,
                mHighestCount, mHigherEq, evalBool(bool));
        return op;
    }

    public static Query create(String term) {
        if (term.charAt(0) == '<') {
            boolean eq = false;
            if (term.charAt(1) == '=') {
                eq = true;
                term = term.substring(2);
            } else {
                term = term.substring(1);
            }
            int num = Integer.parseInt(term);
            return new ConvCountQuery(-1, false, num, eq);
        } else if (term.charAt(0) == '>') {
            boolean eq = false;
            if (term.charAt(1) == '=') {
                eq = true;
                term = term.substring(2);
            } else {
                term = term.substring(1);
            }
            int num = Integer.parseInt(term);
            return new ConvCountQuery(num, eq, -1, false);
        } else {
            int num = Integer.parseInt(term);
            return new ConvCountQuery(num, true, num, true);
        }
    }
}
