// Docs generated with Claude Code.
package com.github.meeplemeet.model.discussions

import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.account.CreateAccountViewModel
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
    private val discussionRepository: DiscussionRepository = RepositoryProvider.discussions
) : CreateAccountViewModel() {
  /**
   * Creates a new discussion with the specified participants.
   *
   * If the name is blank, it defaults to "{creator's name}'s discussion".
   *
   * @param name The name of the discussion
   * @param description The description of the discussion
   * @param creator The account creating the discussion
   * @param participants Accounts to add as participants (vararg)
   */
  fun createDiscussion(
      name: String,
      description: String,
      creator: Account,
      vararg participants: Account
  ) {
    viewModelScope.launch {
      discussionRepository.createDiscussion(
          name.ifBlank { "${creator.name}'s discussion" },
          description,
          creator.uid,
          participants.map { it.uid })
    }
  }
}
