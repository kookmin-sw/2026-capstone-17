package com.capstone.focus.auth.security.service

import com.capstone.focus.domain.entity.enum.MemberRole
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

class FocusMemberDetails(
    private val memberId: String,
    private val email: String?,
    private val role: MemberRole = MemberRole.USER
) : UserDetails {

    override fun getAuthorities(): MutableCollection<out GrantedAuthority> {
        return mutableListOf(SimpleGrantedAuthority("ROLE_${role.name}"))
    }

    override fun getPassword(): String? = null

    override fun getUsername(): String = email ?: memberId

    fun getMemberId(): String = memberId

    override fun isAccountNonExpired(): Boolean = true
    override fun isAccountNonLocked(): Boolean = true
    override fun isCredentialsNonExpired(): Boolean = true
    override fun isEnabled(): Boolean = true
}