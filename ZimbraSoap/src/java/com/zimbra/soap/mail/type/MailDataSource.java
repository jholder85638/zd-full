/*
 * 
 */

package com.zimbra.soap.mail.type;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import com.zimbra.common.soap.MailConstants;
import com.zimbra.soap.type.DataSource;

@XmlType(propOrder = {})
@XmlRootElement
abstract public class MailDataSource
implements DataSource {

    @XmlAttribute private String id;
    @XmlAttribute private String name;
    @XmlAttribute(name=MailConstants.A_FOLDER) private String folderId;
    @XmlAttribute private Boolean enabled = false;
    @XmlAttribute private Boolean importOnly = false;
    @XmlAttribute private String host;
    @XmlAttribute private Integer port;
    @XmlAttribute(name=MailConstants.A_DS_CONNECTION_TYPE) private MdsConnectionType mdsConnectionType;
    @XmlAttribute private String username;
    @XmlAttribute private String password;
    @XmlAttribute private String pollingInterval;
    @XmlAttribute private String emailAddress;
    @XmlAttribute private Boolean useAddressForForwardReply;
    @XmlAttribute private String defaultSignature;
    @XmlAttribute private String fromDisplay;
    @XmlAttribute private String fromAddress;
    @XmlAttribute private String replyToAddress;
    @XmlAttribute private String replyToDisplay;
    @XmlAttribute private Long failingSince;
    @XmlElement String lastError;

    public MailDataSource() {
    }
    
    public MailDataSource(DataSource from) {
        copy(from);
    }
    
    @Override
    public void copy(DataSource from) {
        id = from.getId();
        name = from.getName();
        folderId = from.getFolderId();
        enabled = from.isEnabled();
        importOnly = from.isImportOnly();
        host = from.getHost();
        port = from.getPort();
        mdsConnectionType = MdsConnectionType.CT_TO_MCT.apply(from.getConnectionType());
        username = from.getUsername();
        password = from.getPassword();
        pollingInterval = from.getPollingInterval();
        emailAddress = from.getEmailAddress();
        useAddressForForwardReply = from.isUseAddressForForwardReply();
        defaultSignature = from.getDefaultSignature();
        fromDisplay = from.getFromDisplay();
        fromAddress = from.getFromAddress();
        replyToAddress = from.getReplyToAddress();
        replyToDisplay = from.getReplyToDisplay();
        failingSince = from.getFailingSince();
        lastError = from.getLastError();
    }
    
    @Override
    public String getId() {
        return id;
    }
    
    @Override
    public void setId(String id) {
        this.id = id;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public void setName(String name) {
        this.name = name;
    }
    
    @Override
    public String getFolderId() {
        return folderId;
    }
    
    @Override
    public void setFolderId(String folderId) {
        this.folderId = folderId;
    }
    
    @Override
    public Boolean isEnabled() {
        return enabled;
    }
    
    @Override
    public void setEnabled(Boolean isEnabled) {
        this.enabled = isEnabled;
    }
    
    @Override
    public Boolean isImportOnly() {
        return importOnly;
    }
    
    @Override
    public void setImportOnly(Boolean isImportOnly) {
        this.importOnly = isImportOnly;
    }
    
    @Override
    public String getHost() {
        return host;
    }
    
    @Override
    public void setHost(String host) {
        this.host = host;
    }
    
    @Override
    public Integer getPort() {
        return port;
    }
    
    @Override
    public void setPort(Integer port) {
        this.port = port;
    }

    public MdsConnectionType getMdsConnectionType() {
        return mdsConnectionType;
    }
    
    public void setMdsConnectionType(MdsConnectionType ct) {
        mdsConnectionType = ct;
    }
    
    @Override
    public ConnectionType getConnectionType() {
        return MdsConnectionType.MCT_TO_CT.apply(mdsConnectionType);
    }
    
    @Override
    public void setConnectionType(ConnectionType connectionType) {
        this.mdsConnectionType = MdsConnectionType.CT_TO_MCT.apply(connectionType);
    }
    
    @Override
    public String getUsername() {
        return username;
    }
    
    @Override
    public void setUsername(String username) {
        this.username = username;
    }
    
    @Override
    public String getPassword() {
        return password;
    }
    
    @Override
    public void setPassword(String password) {
        this.password = password;
    }
    
    @Override
    public String getPollingInterval() {
        return pollingInterval;
    }
    
    @Override
    public void setPollingInterval(String pollingInterval) {
        this.pollingInterval = pollingInterval;
    }
    
    @Override
    public String getEmailAddress() {
        return emailAddress;
    }
    
    @Override
    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }
    
    @Override
    public Boolean isUseAddressForForwardReply() {
        return useAddressForForwardReply;
    }
    
    @Override
    public void setUseAddressForForwardReply(Boolean useAddressForForwardReply) {
        this.useAddressForForwardReply = useAddressForForwardReply;
    }
    
    @Override
    public String getDefaultSignature() {
        return defaultSignature;
    }
    
    @Override
    public void setDefaultSignature(String defaultSignature) {
        this.defaultSignature = defaultSignature;
    }
    
    @Override
    public String getFromDisplay() {
        return fromDisplay;
    }
    
    @Override
    public void setFromDisplay(String fromDisplay) {
        this.fromDisplay = fromDisplay;
    }
    
    @Override
    public String getFromAddress() {
        return fromAddress;
    }
    
    @Override
    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }
    
    @Override
    public String getReplyToAddress() {
        return replyToAddress;
    }
    
    @Override
    public void setReplyToAddress(String replyToAddress) {
        this.replyToAddress = replyToAddress;
    }
    
    @Override
    public String getReplyToDisplay() {
        return replyToDisplay;
    }
    
    @Override
    public void setReplyToDisplay(String replyToDisplay) {
        this.replyToDisplay = replyToDisplay;
    }
    
    @Override
    public Long getFailingSince() {
        return failingSince;
    }
    
    @Override
    public void setFailingSince(Long failingSince) {
        this.failingSince = failingSince;
    }
    
    @Override
    public String getLastError() {
        return lastError;
    }
    
    @Override
    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
}
