/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2014-2016 ForgeRock AS.
 */

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
