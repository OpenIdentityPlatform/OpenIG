import org.forgerock.opendj.ldap.Entry
import org.forgerock.opendj.ldap.LinkedAttribute



/*
 * Make LDAP attributes properties of an LDAP entry so that they can
 * be accessed using the dot operator. The setter explicitly
 * constructs an Attribute in order to take advantage of the various
 * overloaded constructors. In particular, it allows scripts to
 * assign multiple values at once (see unit tests for examples).
 */
Entry.metaClass.getProperty = { key ->
    delegate.getAttribute(key)
}
Entry.metaClass.setProperty = { key, values ->
    delegate.replaceAttribute(new LinkedAttribute(key, values))
}
