/*
 * 
 */

package com.zimbra.soap.account.type;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlType;

import com.google.common.collect.Iterables;


/*
     <childAccount name="{child-account-name}" visible="0|1" id="{child-account-id}">
         <attrs>
            <attr name="{name}">{value}</attr>*
         </attrs>
     </childAccount>*

 */
@XmlType (propOrder = {})
public class ChildAccount {

    @XmlAttribute private String name;
    @XmlAttribute(name="visible") private boolean isVisible;
    @XmlAttribute private String id;
    
    @XmlElementWrapper(name="attrs")
    @XmlElement(name="attr")
    private List<Attr> attrs = new ArrayList<Attr>();

    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public boolean isVisible() {
        return isVisible;
    }

    public void setVisible(boolean isVisible) {
        this.isVisible = isVisible;
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public List<Attr> getAttrs() {
        return Collections.unmodifiableList(attrs);
    }
    
    public void setAttrs(Iterable<Attr> attrs) {
        this.attrs.clear();
        if (attrs != null) {
            Iterables.addAll(this.attrs, attrs);
        }
    }
}
