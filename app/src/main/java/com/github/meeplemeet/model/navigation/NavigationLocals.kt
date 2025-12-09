package com.github.meeplemeet.model.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import com.github.meeplemeet.model.MainActivityViewModel

var LocalNavigationVM =
    staticCompositionLocalOf<MainActivityViewModel> { error("LocalNavigationVM not provided") }
