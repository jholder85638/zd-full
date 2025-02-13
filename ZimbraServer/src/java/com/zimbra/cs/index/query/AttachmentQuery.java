/*
 * 
 */
package com.zimbra.cs.index.query;

import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.zimbra.cs.index.LuceneFields;

/**
 * Query by attachment type.
 *
 * @author tim
 * @author ysasaki
 */
public final class AttachmentQuery extends LuceneQuery {
    static final Map<String, String> MAP = ImmutableMap.<String, String>builder()
        .put("any", "any")
        .put("application", "application")
        .put("application/*", "application")
        .put("bmp", "image/bmp")
        .put("image/bmp", "image/bmp")
        .put("gif", "image/gif")
        .put("image/gif", "image/gif")
        .put("image", "image")
        .put("image/*", "image")
        .put("jpeg", "image/jpeg")
        .put("image/jpeg", "image/jpeg")
        .put("excel", "application/vnd.ms-excel")
        .put("application/vnd.ms-excel", "application/vnd.ms-excel")
        .put("xls", "application/vnd.ms-excel")
        .put("ppt", "application/vnd.ms-powerpoint")
        .put("application/vnd.ms-powerpoint", "application/vnd.ms-powerpoint")
        .put("ms-tnef", "application/ms-tnef")
        .put("application/ms-tnef", "application/ms-tnef")
        .put("word", "application/msword")
        .put("application/msword", "application/msword")
        .put("msword", "application/msword")
        .put("none", "none")
        .put("pdf", "application/pdf")
        .put("application/pdf", "application/pdf")
        .put("text", "text")
        .put("text/*", "text")
        .build();

    public AttachmentQuery(String what) {
        super("attachment:", LuceneFields.L_ATTACHMENTS, lookup(MAP, what));
    }

}
