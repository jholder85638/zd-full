/*
 * 
 */
package com.zimbra.cs.index.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.index.LuceneFields;
import com.zimbra.cs.mailbox.Mailbox;

/**
 * Query messages "to me", "from me", "cc me" or any combination thereof.
 *
 * @author tim
 * @author ysasaki
 */
public final class MeQuery extends SubQuery {

    private MeQuery(List<Query> clauses) {
        super(clauses);
    }

    public static Query create(Mailbox mbox, Analyzer analyzer,
            Set<AddrQuery.Address> addrs) throws ServiceException {
        List<Query> clauses = new ArrayList<Query>();
        Account acct = mbox.getAccount();
        boolean atFirst = true;
        if (addrs.contains(AddrQuery.Address.FROM)) {
            clauses.add(new SentQuery(mbox, true));
            atFirst = false;
        }
        if (addrs.contains(AddrQuery.Address.TO)) {
            if (atFirst) {
                atFirst = false;
            } else {
                clauses.add(new ConjQuery(ConjQuery.Conjunction.OR));
            }
            clauses.add(new TextQuery(mbox, analyzer, LuceneFields.L_H_TO, acct.getName()));
        }
        if (addrs.contains(AddrQuery.Address.CC)) {
            if (atFirst) {
                atFirst = false;
            } else {
                clauses.add(new ConjQuery(ConjQuery.Conjunction.OR));
            }
            clauses.add(new TextQuery(mbox, analyzer, LuceneFields.L_H_CC, acct.getName()));
        }

        for (String alias : acct.getMailAlias()) {
            if (addrs.contains(AddrQuery.Address.TO)) {
                if (atFirst) {
                    atFirst = false;
                } else {
                    clauses.add(new ConjQuery(ConjQuery.Conjunction.OR));
                }
                clauses.add(new TextQuery(mbox, analyzer, LuceneFields.L_H_TO, alias));
            }
            if (addrs.contains(AddrQuery.Address.CC)) {
                if (atFirst) {
                    atFirst = false;
                } else {
                    clauses.add(new ConjQuery(ConjQuery.Conjunction.OR));
                }
                clauses.add(new TextQuery(mbox, analyzer, LuceneFields.L_H_CC, alias));
            }
        }
        return new MeQuery(clauses);
    }
}
