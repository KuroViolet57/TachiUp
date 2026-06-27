package com.tachiup.install

sealed class InstallResult {
    object Success : InstallResult()
    data class Failure(val message: String) : InstallResult()
    /** The system package installer UI was launched; the user must confirm. */
    object PendingUserAction : InstallResult()
}
