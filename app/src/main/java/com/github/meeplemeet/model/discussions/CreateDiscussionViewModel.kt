package com.github.meeplemeet.model.discussions

import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.auth.CreateAccountViewModel
import kotlinx.coroutines.launch

class CreateDiscussionViewModel(
    private val discussionRepository: DiscussionRepository = RepositoryProvider.discussions
) : CreateAccountViewModel() {
  /** Create a new discussion. */
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
