/*
 * 
 */
package com.zimbra.cs.gal;

import org.dom4j.QName;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.AccountConstants;
import com.zimbra.common.soap.Element;
import com.zimbra.common.soap.MailConstants;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.AuthToken;
import com.zimbra.cs.account.DataSource;
import com.zimbra.cs.account.Domain;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Provisioning.AccountBy;
import com.zimbra.cs.account.Provisioning.SearchGalResult;
import com.zimbra.cs.account.gal.GalOp;
import com.zimbra.cs.account.gal.GalUtil;
import com.zimbra.cs.index.MailboxIndex;
import com.zimbra.cs.index.SearchParams;
import com.zimbra.cs.index.SortBy;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.soap.ZimbraSoapContext;

public class GalSearchParams {
	private GalSearchConfig mConfig;
	private Provisioning.GalSearchType mType = Provisioning.GalSearchType.account;
	private int mLimit;
	private Integer mLdapLimit; // ldap search does not support paging, allow a different limit for ldap search
	private int mPageSize;
	private String mQuery;
	private GalSyncToken mSyncToken;
	private SearchGalResult mResult;
	private ZimbraSoapContext mSoapContext;
	
	private Account mAccount;
	private String mUserAgent;
	private Account mGalSyncAccount;
	private Domain mDomain;
    private SearchParams mSearchParams;
    private GalSearchResultCallback mResultCallback;
    private GalSearchQueryCallback mExtraQueryCallback;
    private Element mRequest;
    private QName mResponse;
    private DataSource mDataSource;
    private boolean mIdOnly;
    private boolean mNeedCanExpand;
    private boolean mNeedSMIMECerts;
    private boolean mFetchGroupMembers;
    private GalOp mOp;
	
	public GalSearchParams(Account account) {
        mAccount = account;
        mResult = SearchGalResult.newSearchGalResult(null);
        mResponse = AccountConstants.SEARCH_GAL_RESPONSE;
	}
	
	public GalSearchParams(Account account, ZimbraSoapContext ctxt) {
		this(account);
		mSoapContext = ctxt;
	}
	
    public GalSearchParams(Domain domain, ZimbraSoapContext ctxt) {
    	mDomain = domain;
    	mSoapContext = ctxt;
    }
    
    public GalSearchParams(DataSource ds) throws ServiceException {
    	this(ds.getAccount());
    	mDataSource = ds;
		mConfig = GalSearchConfig.create(mDataSource);
    }
    
	public GalSearchConfig getConfig() {
		return mConfig;
	}
	
	public Provisioning.GalSearchType getType() {
		return mType;
	}

	public int getLimit() {
		return mLimit;
	}
	
	public Integer getLdapLimit() {
	    return mLdapLimit;
	}

	public int getPageSize() {
		return mPageSize;
	}

	public String getQuery() {
		return mQuery;
	}
	
	public String getSyncToken() {
		if (mSyncToken == null)
			return null;
		return mSyncToken.getLdapTimestamp();
	}
	
	public GalSyncToken getGalSyncToken() {
		return mSyncToken;
	}
	
	public SearchGalResult getResult() {
		return mResult;
	}
	
	public Account getAccount() {
		return mAccount;
	}
	
	public Account getGalSyncAccount() {
		return mGalSyncAccount;
	}
	
	public Domain getDomain() throws ServiceException {
        if (mDomain != null)
            return mDomain;
        
        Domain domain = Provisioning.getInstance().getDomain(mAccount);
        if (domain != null)
            return domain;
        
        Account galSyncAcct = getGalSyncAccount();
        if (galSyncAcct != null)
            domain = Provisioning.getInstance().getDomain(galSyncAcct);
        
        if (domain != null)
            return domain;
        
        throw ServiceException.FAILURE("Unable to get domain", null);
    }
	
	public ZimbraSoapContext getSoapContext() {
		return mSoapContext;
	}
	
	public AuthToken getAuthToken() {
	    if (mSoapContext == null)
	        return null;
	    else
	        return mSoapContext.getAuthToken();
	}
	
    public Account getAuthAccount() throws ServiceException {
	    if (mSoapContext == null)
            return getAccount();
        else
            return Provisioning.getInstance().get(AccountBy.id, mSoapContext.getAuthtokenAccountId());
    }
    
	public SearchParams getSearchParams() {
		return mSearchParams;
	}

	public GalSearchResultCallback getResultCallback() {
		if (mResultCallback == null)
			return createResultCallback();
		return mResultCallback;
	}
	
    public GalSearchQueryCallback getExtraQueryCallback() {
        return mExtraQueryCallback;
    }
	   
	public Element getRequest() {
		return mRequest;
	}
	
	public QName getResponseName() {
		return mResponse;
	}
    
    public GalOp getOp() {
        return mOp;
    }
	
	public boolean isIdOnly() {
		return mIdOnly;
	}
	
    public boolean getNeedCanExpand() {
        return mNeedCanExpand;
    }
    
    public boolean getNeedSMIMECerts() {
        return mNeedSMIMECerts;
    }
    
	public void setSearchConfig(GalSearchConfig config) {
		mConfig = config;
	}
	
	public void setType(Provisioning.GalSearchType type) {
		mType = type;
	}
	
	public void setLimit(int limit) {
		mLimit = limit;
	}
	
    public void setLdapLimit(int limit) {
        mLdapLimit = limit;
    }
	
	public void setPageSize(int pageSize) {
		mPageSize = pageSize;
	}
	
	public void setQuery(String query) {
		mQuery = query;
	}
	   
	public void setToken(String token) {
		mSyncToken = new GalSyncToken(token);
	}
	
	public void setGalResult(SearchGalResult result) {
		mResult = result;
	}
	
	public void createSearchParams(String searchQuery) {
		mSearchParams = new SearchParams();
		mSearchParams.setLimit(mLimit + 1);
		mSearchParams.setSortBy(SortBy.NAME_ASCENDING);
		mSearchParams.setQueryStr(searchQuery);
	    mSearchParams.setTypes(new byte[] { MailItem.TYPE_CONTACT });
	}
	
	public void parseSearchParams(Element request, String searchQuery) throws ServiceException {
		if (request == null || mSoapContext == null) {
			createSearchParams(searchQuery);
			return;
		}
		setRequest(request);
		
		// bug 69338
		// SearchParams.parse relies on A_SEARCH_TYPES on the request to determine search type, 
		// which will then determine if cursor should be used to narrow db query.  
		// If A_SEARCH_TYPES is not set, default type is conversation, cursor is not used to 
		// narrow db query for conversations.   We do not require clients to set types 
		// on GAL soap APIs.  Set it to "contact" here.
		request.addAttribute(MailConstants.A_SEARCH_TYPES, MailboxIndex.SEARCH_FOR_CONTACTS);
		mSearchParams = SearchParams.parse(request, mSoapContext, searchQuery);
		mSearchParams.setTypes(new byte[] { MailItem.TYPE_CONTACT });
		setLimit(mSearchParams.getLimit());
	}
	
	public void setResultCallback(GalSearchResultCallback callback) {
		mResultCallback = callback;
	}
	
	public GalSearchResultCallback createResultCallback() {
		mResultCallback = new GalSearchResultCallback(this);
		return mResultCallback;
	}
	
    public void setExtraQueryCallback(GalSearchQueryCallback callback) {
        mExtraQueryCallback = callback;
    }
	
	public void setRequest(Element req) {
		mRequest = req;
	}
	public void setResponseName(QName response) {
		mResponse = response;
	}
	
	public void createSearchConfig(GalSearchConfig.GalType type) throws ServiceException {
		mConfig = GalSearchConfig.create(getDomain(), mOp, type, mType);
		mConfig.getRules().setFetchGroupMembers(mFetchGroupMembers);
		mConfig.getRules().setNeedSMIMECerts(mNeedSMIMECerts);
	}
	
	public String generateLdapQuery() throws ServiceException {
		assert(mConfig != null);
		String token = (mSyncToken != null) ? mSyncToken.getLdapTimestamp(mConfig.mTimestampFormat) : null;
		
		String extraQuery = null;
		if (GalSearchConfig.GalType.zimbra == mConfig.getGalType() && mExtraQueryCallback != null) {
		    extraQuery = mExtraQueryCallback.getZimbraLdapSearchQuery();
		}
		return GalUtil.expandFilter(mConfig.getTokenizeKey(), mConfig.getFilter(), mQuery, token, extraQuery);
	}
	
	public void setGalSyncAccount(Account acct) {
		mGalSyncAccount = acct;
	}
	
	public void setIdOnly(boolean idOnly) {
		mIdOnly = idOnly;
	}
	
    public void setNeedCanExpand(boolean needCanExpand) {
        mNeedCanExpand = needCanExpand;
    }
        
    public void setNeedSMIMECerts(boolean needSMIMECerts) {
        mNeedSMIMECerts = needSMIMECerts;
    }
    
    public void setFetchGroupMembers(boolean fetchGroupMembers) {
        mFetchGroupMembers = fetchGroupMembers;
    }

    public void setOp(GalOp op) {
        mOp = op;
    }

    public void setUserAgent(String ua) {
        mUserAgent = ua;
    }

    public String getUserInfo() {
        if (mAccount != null) {
            return mAccount.getName() + " (" + ((mUserAgent == null) ? "" : mUserAgent) + ")";
        } else {
            return " (" + ((mUserAgent == null) ? "" : mUserAgent) + ")";
        }
    }
}