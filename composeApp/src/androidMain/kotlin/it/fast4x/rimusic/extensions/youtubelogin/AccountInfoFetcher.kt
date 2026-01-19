package it.fast4x.rimusic.extensions.youtubelogin

import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.AccountInfo
import it.fast4x.innertube.models.AccountMenuResponse
import timber.log.Timber

class AccountInfoFetcher {
    
    companion object {
        suspend fun fetchAccountInfo(): AccountInfo? {
            return try {
                Timber.d("AccountInfoFetcher: Starting account info fetch")
                
                // Try to get account menu first (more reliable for logged-in users)
                val menuResult = try {
                    Innertube.accountMenu()
                } catch (e: Exception) {
                    Timber.e("AccountInfoFetcher: Error calling accountMenu: ${e.message}")
                    null
                }
                
                // Check if menuResult is an AccountMenuResponse
                if (menuResult is AccountMenuResponse) {
                    Timber.d("AccountInfoFetcher: Got AccountMenuResponse")
                    
                    // Extract account info using the toAccountInfo() method
                    val accountInfo = menuResult.actions?.firstOrNull()?.openPopupAction?.popup
                        ?.multiPageMenuRenderer?.header?.activeAccountHeaderRenderer?.toAccountInfo()
                    
                    if (accountInfo != null) {
                        Timber.d("AccountInfoFetcher: Successfully extracted account info from menu")
                        return accountInfo
                    }
                }
                
                // Fallback to accountInfo() if menu fails
                Timber.d("AccountInfoFetcher: Trying accountInfo() as fallback")
                val accountInfoResult = try {
                    Innertube.accountInfo()
                } catch (e: Exception) {
                    Timber.e("AccountInfoFetcher: Error calling accountInfo: ${e.message}")
                    null
                }
                
                accountInfoResult?.getOrNull()?.let {
                    Timber.d("AccountInfoFetcher: Got account info from fallback")
                    return it
                }
                
                Timber.d("AccountInfoFetcher: Could not extract account info from any method")
                null
                
            } catch (e: Exception) {
                Timber.e("AccountInfoFetcher: Error fetching account info: ${e.message}")
                null
            }
        }
    }
}