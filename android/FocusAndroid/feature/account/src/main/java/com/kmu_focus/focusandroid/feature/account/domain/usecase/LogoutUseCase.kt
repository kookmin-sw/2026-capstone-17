package com.kmu_focus.focusandroid.feature.account.domain.usecase

import com.kmu_focus.focusandroid.feature.account.domain.repository.AccountRepository
import com.kmu_focus.focusandroid.feature.auth.domain.session.AuthSessionManager
import javax.inject.Inject

class LogoutUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
    private val authSessionManager: AuthSessionManager,
) {
    suspend operator fun invoke(): Result<Unit> {
        val result = accountRepository.logout()
        if (result.isSuccess) {
            authSessionManager.update(false)
        }
        return result
    }
}
