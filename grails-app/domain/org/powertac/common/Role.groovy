package org.powertac.common

/**
 * File generated by Spring Security for user management. Not serializable
 * or public.
 */

class Role {

	String authority

	static mapping = {
		cache true
	}

	static constraints = {
		authority blank: false, unique: true
	}
}
