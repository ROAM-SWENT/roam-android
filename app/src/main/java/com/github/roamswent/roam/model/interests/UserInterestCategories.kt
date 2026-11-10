// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>

package com.github.roamswent.roam.model.interests

import androidx.annotation.StringRes
import com.github.roamswent.roam.R

data class InterestCategory(val id: String, @param:StringRes val labelRes: Int)

object UserInterestCategories {
  val all =
      listOf(
          InterestCategory("Architecture", R.string.interest_architecture),
          InterestCategory("History", R.string.interest_history),
          InterestCategory("Art & Sculpture", R.string.interest_art_sculpture),
          InterestCategory("Local Culture", R.string.interest_local_culture),
          InterestCategory("Legends & Myths", R.string.interest_legends_myths),
          InterestCategory("Fun Facts", R.string.interest_fun_facts),
          InterestCategory("Nature", R.string.interest_nature),
          InterestCategory("Food & Drink", R.string.interest_food_drink),
      )
  val ids = all.map { it.id }
}
