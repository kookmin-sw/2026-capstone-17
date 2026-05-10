package com.kmu_focus.focusandroid.feature.account.domain.usecase

import com.kmu_focus.focusandroid.feature.account.domain.repository.AccountRepository
import javax.inject.Inject

class DisconnectChzzkUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
) {
    suspend operator fun invoke(): Result<Unit> {
        return accountRepository.disconnectChzzk()
    }
}
