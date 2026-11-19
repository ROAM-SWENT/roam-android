// Co-authored-by: Gemini 3.8 Flash <noreply@google.com>

package com.github.roamswent.roam.model.guide

interface AccountPrivacyProvider {
  suspend fun getPrivacyState(): AccountPrivacyState

  suspend fun isPublicAccount(): Boolean
}

class StubAccountPrivacyProvider(private val state: AccountPrivacyState = AccountPrivacyState()) :
    AccountPrivacyProvider {
  override suspend fun getPrivacyState(): AccountPrivacyState = state

  override suspend fun isPublicAccount(): Boolean = state.isPublic
}
