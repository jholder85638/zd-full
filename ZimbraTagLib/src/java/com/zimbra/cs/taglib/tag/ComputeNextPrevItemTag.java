/*
 * 
 */
package com.zimbra.cs.taglib.tag;

import com.zimbra.cs.taglib.bean.NextPrevItemBean;
import com.zimbra.cs.taglib.bean.ZSearchResultBean;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.PageContext;
import java.io.IOException;

public class ComputeNextPrevItemTag extends ZimbraSimpleTag {


    private String mVar;
    private int mIndex;
    private ZSearchResultBean mSearchResult;

    public void setVar(String var) { this.mVar = var; }

    public void setSearchResult(ZSearchResultBean result) {this.mSearchResult = result; }

    public void setIndex(int index) {this.mIndex = index; }

    public void doTag() throws JspException, IOException {

        PageContext pageContext = (PageContext) getJspContext();

        int prevIndex = mIndex;
        int prevOffset = mSearchResult.getOffset();
        int nextIndex = mIndex;
        int nextOffset = mSearchResult.getOffset();
        boolean hasPrev = true;
        boolean hasNext = true;

        if (mIndex > 0) {
            prevIndex = mIndex - 1;
        } else if (mSearchResult.getOffset() > 0) {
            prevOffset = mSearchResult.getPrevOffset();
            prevIndex = mSearchResult.getLimit()-1;
        } else {
            hasPrev = false;
        }
        
        if (mIndex < mSearchResult.getHits().size() -1) {
            nextIndex = mIndex + 1;
        } else if (mSearchResult.getHasMore()) {
            nextOffset = mSearchResult.getNextOffset();
            nextIndex = 0;
        } else {
            hasNext = false;
        }

        NextPrevItemBean result = new NextPrevItemBean(hasPrev, prevIndex, prevOffset, hasNext, nextIndex, nextOffset);
        pageContext.setAttribute(mVar, result, PageContext.REQUEST_SCOPE);

    }
}
