// Docs generated with Claude Code.
package com.github.meeplemeet.model.discussions

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.account.AccountRepository
import com.github.meeplemeet.model.account.CreateAccountViewModel
import com.github.meeplemeet.model.account.NotificationSettings
import com.github.meeplemeet.model.account.RelationshipStatus
import com.github.meeplemeet.model.offline.OfflineModeManager
import kotlinx.coroutines.launch

/**
 * ViewModel for creating new discussions.
 *
 * This ViewModel extends [CreateAccountViewModel] to provide account management functionality along
 * with discussion creation capabilities.
 *
 * @property discussionRepository Repository for discussion operations
 */
class CreateDiscussionViewModel(
    private val accountRepository: AccountRepository = RepositoryProvider.accounts,
    private val discussionRepository: DiscussionRepository = RepositoryProvider.discussions,
) : CreateAccountViewModel() {
  /**
   * Creates a new discussion with the specified participants.
   *
   * If the name is blank, it defaults to "{creator's name}'s discussion".
   *
   * This method respects the notification settings of each participant:
   * - Participants with EVERYONE setting are added directly to the discussion
   * - Participants with FRIENDS_ONLY setting are added only if they are friends with the creator
   * - Participants who don't meet these criteria receive a join notification instead
   *
   * @param name The name of the discussion
   * @param description The description of the discussion
   * @param creator The account creating the discussion
   * @param participants Accounts to add as participants (vararg)
   */
  fun createDiscussion(
      context: Context,
      name: String,
      description: String,
      creator: Account,
      vararg participants: Account
  ) {
    viewModelScope.launch {
      // Add all the participants that accept friend request from everyone or only there friends
      val participantsToAdd =
          participants.filter { account ->
            account.notificationSettings == NotificationSettings.EVERYONE ||
                account.notificationSettings == NotificationSettings.FRIENDS_ONLY &&
                    creator.relationships[account.uid] == RelationshipStatus.FRIEND
          }

      val disc =
          discussionRepository.createDiscussion(
              name.ifBlank { "${creator.name}'s discussion" },
              description,
              creator.uid,
              participantsToAdd.map { it.uid })

      OfflineModeManager.addDiscussionToCache(disc, context)

      // Send a join request to the rest
      participants
          .filterNot { participantsToAdd.contains(it) }
          .forEach { accountRepository.sendJoinDiscussionNotification(it.uid, disc) }
    }
  }
}
